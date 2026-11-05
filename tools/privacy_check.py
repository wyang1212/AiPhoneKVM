#!/usr/bin/env python3
"""Check a public source tree or ZIP without printing matched private values.

A private JSON --denylist may be a string array, or an object containing
``literals`` (string array) and ``patterns`` (objects with regex/category).
Never commit that private file. Static checks are best-effort, not proof that
all private data is absent. Repository history is not inspected.
Use --working-tree only to skip the local root .git directory when checking
an already initialized checkout; archives always reject Git metadata.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath

MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_ARCHIVE_BYTES = 512 * 1024 * 1024
BLOCKED_DIRS = {
    '.git', '.hg', '.svn', '.gradle', '.kotlin', '.idea', '.vscode',
    '__pycache__', '.pytest_cache', '.mypy_cache', '.ruff_cache',
    'node_modules', 'build', 'dist', 'evidence', 'downloads', 'memory',
    'secrets', 'backups', 'shared_prefs', '.ipynb_checkpoints',
    '.android', '.ssh', '.aws', '.kube', '.config', '.cache',
    '.codex', '.agents', '.gstack', 'logs',
}
BLOCKED_NAMES = {
    'agents.md', 'claude.md', 'local.properties', '.ds_store', 'thumbs.db',
    'desktop.ini', '.git', '.gitmodules', '.netrc', '.npmrc', '.pypirc',
    'credentials.json', 'admin.json', 'dynamic-security.json',
    'id_rsa', 'id_ed25519', 'id_ecdsa', 'id_dsa',
}
BLOCKED_SUFFIXES = {
    '.jks', '.keystore', '.pem', '.key', '.p12', '.pfx', '.mobileprovision',
    '.crt', '.cert', '.der', '.apk', '.aab', '.apks', '.xapk', '.pyc',
    '.class', '.dex', '.so', '.o', '.sqlite', '.sqlite3', '.db', '.log',
    '.bak', '.swp', '.swo', '.hprof', '.dmp', '.dump', '.trace',
    '.pcap', '.pcapng', '.har',
}
SAMPLE_ENV_FILES = {'.env.example', '.env.sample'}
ANDROID_AGP_METADATA = 'META-INF/com/android/build/gradle/app-metadata.properties'
# kotlinx-serialization-core-jvm 1.11.0 ships a public SDK verification marker.
# Exempt only its exact bytes from generic credential heuristics inside Android
# artifacts; all provider-key patterns and private denylist rules still apply.
ANDROID_SDK_VERIFICATION_HASHES = {
    'META-INF/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/verification.properties':
        '7015ed9d593ee3f2151abd95c5067be59cd95ba8d62a26106fc2c8572d81b2fb',
}
SECRET_PATTERNS = {
    'private-key': re.compile(r'-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----'),
    'provider-api-key': re.compile(r'\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}\b'),
    'github-token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{50,})\b'),
    'cloud-access-key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'google-api-key': re.compile(r'\bAIza[0-9A-Za-z_-]{35}\b'),
    'private-home-path': re.compile(r'/(?:home|Users)/(?!(?:runner|vscode|example)(?:/|\b))[A-Za-z0-9_.-]+/'),
    'private-windows-path': re.compile(r'(?i)\b[A-Z]:[\\/]Users[\\/](?!runneradmin[\\/])[A-Za-z0-9_.-]+[\\/]'),
}
CREDENTIAL_LITERAL = re.compile(
    r'''(?i)["']?(?:api[_-]?key|access[_-]?token|capture[_-]?token|token|client[_-]?secret|mqtt[_-]?password|password)["']?\s*[:=]\s*["']([^"'\r\n]{16,})["']'''
)

CREDENTIAL_ENV = re.compile(
    r'(?im)^\s*(?:export\s+)?[A-Za-z0-9_]*(?:KEY|TOKEN|PASSWORD|SECRET)\s*=\s*([^\r\n#]+)'
)


def _looks_credential(value: str) -> bool:
    value = value.strip().strip('\"\'')
    placeholder = re.match(r'(?i)(?:example|dummy|placeholder|replace|your[_-]|test[_-]|change.?me)', value)
    return (len(value) >= 16 and not placeholder and not value.startswith(('$', '<'))
            and re.fullmatch(r'[A-Za-z0-9_./+=!@#$%^&*:-]+', value) is not None
            and len(set(value)) >= 8 and any(c.isdigit() for c in value)
            and any(c.isalpha() for c in value))


@dataclass(frozen=True)
class Rule:
    category: str
    literal: str | None = None
    regex: re.Pattern[str] | None = None


def load_denylist(paths: list[str]) -> list[Rule]:
    rules: list[Rule] = []
    for filename in paths:
        try:
            raw = json.loads(Path(filename).read_text(encoding='utf-8'))
        except (OSError, ValueError) as exc:
            raise ValueError('Cannot read private denylist JSON; contents were not logged') from exc
        if isinstance(raw, list):
            raw = {'literals': raw}
        if not isinstance(raw, dict) or set(raw) - {'literals', 'patterns'}:
            raise ValueError('Denylist must contain only literals and patterns')
        literals = raw.get('literals', [])
        patterns = raw.get('patterns', [])
        if not isinstance(literals, list) or not isinstance(patterns, list):
            raise ValueError('Denylist literals/patterns must be arrays')
        for value in literals:
            if not isinstance(value, str) or not value:
                raise ValueError('Denylist literals must be nonempty strings')
            rules.append(Rule('private-denylist-literal', literal=value))
        for item in patterns:
            if not isinstance(item, dict) or not isinstance(item.get('regex'), str):
                raise ValueError('Denylist pattern must contain a regex string')
            category = item.get('category', 'private-denylist-pattern')
            if not isinstance(category, str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,48}', category):
                category = 'private-denylist-pattern'
            try:
                compiled = re.compile(item['regex'], re.IGNORECASE)
            except re.error as exc:
                raise ValueError('Invalid private denylist pattern; expression was not logged') from exc
            rules.append(Rule(category, regex=compiled))
    return rules


def forbidden_path(name: str) -> str | None:
    if re.match(r'^[A-Za-z]:[\\/]', name):
        return 'unsafe-path'
    path = PurePosixPath(name.replace('\\', '/'))
    if path.is_absolute() or '..' in path.parts:
        return 'unsafe-path'
    lower = [p.lower() for p in path.parts]
    for index, part in enumerate(lower[:-1]):
        if part == 'data' and not ('src' in lower[:index] and any(section in lower[:index] for section in ('java', 'kotlin'))):
            return 'runtime-or-local-directory'
        if part in BLOCKED_DIRS or part.startswith('.venv') or part in {'venv', 'env'}:
            return 'runtime-or-local-directory'
    base = lower[-1] if lower else ''
    if base in BLOCKED_NAMES or base.endswith('~'):
        return 'local-or-credential-file'
    if base.startswith('.env') and base not in SAMPLE_ENV_FILES:
        return 'runtime-environment-file'
    if path.suffix.lower() in BLOCKED_SUFFIXES:
        return 'runtime-or-credential-artifact'
    return None


def _redact_path(name: str, rules: list[Rule]) -> str:
    safe = name
    for rule in rules:
        if rule.literal:
            safe = re.sub(re.escape(rule.literal), '<redacted>', safe, flags=re.IGNORECASE)
        elif rule.regex:
            safe = rule.regex.sub('<redacted>', safe)
    for regex in SECRET_PATTERNS.values():
        safe = regex.sub('<redacted>', safe)
    return safe


def _image_metadata(data: bytes) -> bool:
    if data.startswith(b'\x89PNG\r\n\x1a\n'):
        offset = 8
        while offset + 12 <= len(data):
            size = struct.unpack('>I', data[offset:offset + 4])[0]
            kind = data[offset + 4:offset + 8]
            if size > len(data) - offset - 12:
                return True
            if kind in {b'tEXt', b'zTXt', b'iTXt', b'eXIf'}:
                return True
            offset += size + 12
            if kind == b'IEND':
                break
    # EXIF/XMP often carry owner/GPS/serial information; report presence, never values.
    if data.startswith(b'\xff\xd8') and (b'Exif\x00\x00' in data or b'http://ns.adobe.com/xap/' in data):
        return True
    return False


def scan_bytes(name: str, data: bytes, rules: list[Rule], android_artifact: bool = False) -> list[dict]:
    findings: list[dict] = []
    safe_path = _redact_path(name, rules)
    text = data.decode('utf-8', errors='replace')
    def add(category: str, offset: int | None = None):
        finding = {'path': safe_path, 'category': category}
        if offset is not None:
            finding['line'] = text.count('\n', 0, offset) + 1
        if finding not in findings:
            findings.append(finding)
    if safe_path != name:
        add('private-path-metadata')
    for category, regex in SECRET_PATTERNS.items():
        for match in regex.finditer(text):
            add(category, match.start())
    credential_patterns = [CREDENTIAL_LITERAL]
    if PurePosixPath(name).suffix.lower() in {'.env', '.properties'} or PurePosixPath(name).name in SAMPLE_ENV_FILES:
        credential_patterns.append(CREDENTIAL_ENV)
    known_sdk_verification = (android_artifact and name in ANDROID_SDK_VERIFICATION_HASHES
                              and hashlib.sha256(data).hexdigest() == ANDROID_SDK_VERIFICATION_HASHES[name])
    if not known_sdk_verification:
        for pattern in credential_patterns:
            for match in pattern.finditer(text):
                if _looks_credential(match[1]):
                    add('credential-literal', match.start())
    for rule in rules:
        if rule.literal:
            match = re.search(re.escape(rule.literal), text, re.IGNORECASE)
            if match:
                add(rule.category, match.start())
            # Android resources may store strings as UTF-16; do not limit private checks to text files.
            elif rule.literal.encode('utf-16le').lower() in data.lower() or rule.literal.encode('utf-16be').lower() in data.lower():
                add(rule.category)
        elif rule.regex:
            for match in rule.regex.finditer(text):
                add(rule.category, match.start())
    if _image_metadata(data):
        add('image-metadata-needs-review')
    return findings


def scan_tree(root: Path, rules: list[Rule], working_tree: bool = False) -> dict:
    findings: list[dict] = []
    count = 0
    import os
    for directory, dirs, files in os.walk(root, followlinks=False):
        parent = Path(directory)
        for name in list(dirs):
            path = parent / name
            rel = path.relative_to(root).as_posix()
            if working_tree and parent == root and name == '.git':
                dirs.remove(name)
                continue
            category = forbidden_path(rel + '/_')
            if path.is_symlink():
                category = 'symbolic-link'
            if category:
                findings.append({'path': _redact_path(rel, rules), 'category': category})
                dirs.remove(name)
        for name in files:
            path = parent / name
            rel = path.relative_to(root).as_posix()
            if working_tree and parent == root and name == '.git':
                continue
            category = forbidden_path(rel)
            if path.is_symlink():
                category = 'symbolic-link'
            if category:
                findings.append({'path': _redact_path(rel, rules), 'category': category})
                continue
            try:
                if path.stat().st_size > MAX_FILE_BYTES:
                    raise ValueError
                data = path.read_bytes()
            except ValueError:
                findings.append({'path': _redact_path(rel, rules), 'category': 'oversize-unscanned-file'})
                continue
            except OSError:
                findings.append({'path': _redact_path(rel, rules), 'category': 'unreadable-file'})
                continue
            count += 1
            findings.extend(scan_bytes(rel, data, rules))
    return {'files_scanned': count, 'git_history_scanned': False, 'findings': findings}


def scan_archive(path: Path, rules: list[Rule], android_artifact: bool = False) -> dict:
    findings: list[dict] = []
    count = 0
    total = 0
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            if item.is_dir():
                continue
            name = item.filename
            category = forbidden_path(name)
            if (android_artifact and category == 'runtime-or-local-directory'
                    and name == ANDROID_AGP_METADATA):
                category = None
            if (android_artifact and category == 'runtime-or-credential-artifact'
                    and PurePosixPath(name).suffix.lower() in {'.dex', '.so', '.class'}):
                category = None
            if stat.S_ISLNK(item.external_attr >> 16):
                category = 'symbolic-link'
            total += item.file_size
            if item.file_size > MAX_FILE_BYTES or total > MAX_ARCHIVE_BYTES:
                category = 'archive-size-limit'
            if category:
                findings.append({'path': _redact_path(name, rules), 'category': category})
                continue
            count += 1
            findings.extend(scan_bytes(name, archive.read(item), rules, android_artifact))
    return {'files_scanned': count, 'git_history_scanned': False, 'findings': findings}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target', type=Path)
    parser.add_argument('--denylist', action='append', default=[], metavar='PRIVATE_JSON')
    parser.add_argument('--working-tree', action='store_true', help='Skip root .git metadata only; history is not checked')
    parser.add_argument('--json', action='store_true', help='Print locations/categories as JSON')
    args = parser.parse_args()
    try:
        rules = load_denylist(args.denylist)
        if args.target.is_symlink():
            raise ValueError('Target must not be a symbolic link')
        if args.target.is_dir():
            report = scan_tree(args.target, rules, args.working_tree)
        elif zipfile.is_zipfile(args.target):
            report = scan_archive(args.target, rules, android_artifact=args.target.suffix.lower() in {'.apk', '.aab'})
        elif args.target.is_file():
            if args.target.stat().st_size > MAX_FILE_BYTES:
                raise ValueError('Input file exceeds the scan limit')
            report = {'files_scanned': 1, 'git_history_scanned': False,
                      'findings': scan_bytes(args.target.name, args.target.read_bytes(), rules)}
        else:
            raise ValueError('Target does not exist or is unsupported')
    except (OSError, ValueError, zipfile.BadZipFile):
        print('Privacy check failed to read a target or private denylist; contents were not logged', file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(f"Scanned {report['files_scanned']} files; {len(report['findings'])} findings; Git history not checked")
        for finding in report['findings']:
            print(f"{finding['path']}:{finding.get('line', '-')} [{finding['category']}]")
    return 1 if report['findings'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
