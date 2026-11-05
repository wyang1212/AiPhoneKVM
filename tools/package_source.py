#!/usr/bin/env python3
"""Package a sanitized corresponding-source snapshot, without Git history.

--export-dir additionally writes an independent clean directory for publication.
It must be outside this project and empty or nonexistent. A private --denylist
uses the same JSON format as privacy_check.py; no matching value is logged.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import tempfile
import zipfile

from privacy_check import forbidden_path, load_denylist, scan_tree

ROOT_FILES = {
    '.gitignore', 'README.md', 'NOTICE.md', 'THIRD_PARTY_NOTICES.md', 'LICENSE',
    'LICENSE.BlueHIDFlow', 'UPSTREAM-README.md', 'CHANGELOG.md',
    'CONTRIBUTING.md', 'SECURITY.md',
}
TOOLS = {'build_android.py', 'package_source.py', 'privacy_check.py'}
SOURCE_DIRS = {'app', 'capture', 'gradle', 'assets'}
SOURCE_FILES = {
    '.gitignore', 'LICENSE', 'README.md', 'build.gradle.kts', 'settings.gradle.kts',
    'gradle.properties', 'gradlew', 'gradlew.bat',
}
TEXT_SUFFIXES = {'.py', '.js', '.cjs', '.mjs', '.css', '.html', '.md', '.yml', '.yaml', '.conf', '.txt', '.json', '.toml', '.svg'}
IMAGE_SUFFIXES = {'.png', '.jpg', '.jpeg', '.webp'}
# Upstream README-only artwork is unnecessary for building either application.
# Keep original files locally and retain upstream README text and all licenses.
EXCLUDED_README_ARTWORK = {
    'src/assets/icon_round.png', 'src/assets/ko-fi-badge.png',
    'src/assets/liberapay-badge.png',
}


def included(rel: Path) -> bool:
    name = rel.as_posix()
    if name in EXCLUDED_README_ARTWORK:
        return False
    if forbidden_path(name):
        return False
    if len(rel.parts) == 1:
        return rel.name in ROOT_FILES
    top = rel.parts[0]
    if top == 'src':
        if len(rel.parts) == 2:
            return rel.name in SOURCE_FILES
        if rel.parts[1] not in SOURCE_DIRS:
            return False
        # The upstream Gradle wrapper is necessary source-build tooling, not an APK.
        if rel.suffix.lower() in {'.jar', '.aar', '.zip', '.gz', '.tar'}:
            return name == 'src/gradle/wrapper/gradle-wrapper.jar'
        return True
    if top == 'mqtt-portal':
        return rel.suffix.lower() in TEXT_SUFFIXES | IMAGE_SUFFIXES or rel.name in {'Dockerfile', '.gitignore', '.dockerignore', '.env.example', '.env.sample'}
    if top == 'LICENSES':
        return rel.suffix.lower() in {'.txt', '.md', '.html'} or rel.name.upper().startswith(('LICENSE', 'NOTICE', 'COPYING'))
    if top == 'docs':
        # Only reviewed public QA documentation and isolated screenshots belong here.
        return rel.suffix.lower() in TEXT_SUFFIXES | IMAGE_SUFFIXES
    if top == 'tools':
        return len(rel.parts) == 2 and rel.name in TOOLS
    if top == '.github':
        return len(rel.parts) == 3 and rel.parts[1] == 'workflows' and rel.suffix.lower() in {'.yml', '.yaml'}
    return False


def sources(root: Path) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for directory, dirs, files in os.walk(root, followlinks=False):
        parent = Path(directory)
        for name in list(dirs):
            path = parent / name
            rel = path.relative_to(root)
            # Prune local/runtime trees before descending into caches or private data.
            if forbidden_path(rel.as_posix() + '/_') or (len(rel.parts) == 1 and name not in {'src', 'mqtt-portal', 'LICENSES', 'docs', 'tools', '.github'}):
                dirs.remove(name)
                continue
            if path.is_symlink():
                raise ValueError('Symbolic-link source directory cannot be exported')
        for name in files:
            path = parent / name
            rel = path.relative_to(root)
            if not included(rel):
                continue
            if path.is_symlink() or not path.is_file():
                raise ValueError('Symbolic-link or special source file cannot be exported')
            result[rel.as_posix()] = path
    required = {'README.md', 'NOTICE.md', 'THIRD_PARTY_NOTICES.md', 'LICENSE',
                'src/gradle.properties', 'src/gradlew', 'src/gradle/wrapper/gradle-wrapper.jar',
                'tools/build_android.py', 'tools/package_source.py', 'tools/privacy_check.py'}
    if not required.issubset(result):
        raise ValueError('Required corresponding-source files are missing')
    return dict(sorted(result.items()))


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def check_destination(target: Path, root: Path):
    if target.is_symlink():
        raise ValueError('Export directory must not be a symbolic link')
    resolved = target.resolve()
    if resolved == root or resolved.is_relative_to(root):
        raise ValueError('Export directory must be independent of the source project')
    if target.exists() and (not target.is_dir() or any(target.iterdir())):
        raise ValueError('Export directory is not empty; refusing to overwrite Git metadata or user files')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--export-dir', type=Path)
    parser.add_argument('--denylist', action='append', default=[], metavar='PRIVATE_JSON')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        rules = load_denylist(args.denylist)
        if args.export_dir:
            check_destination(args.export_dir, root)
        match = re.search(r'^aiphonekvm.versionName=([0-9]+(?:\.[0-9]+){0,5})$',
                          (root / 'src/gradle.properties').read_text(), re.M)
        if not match:
            raise ValueError('Public version must be a numeric dotted version')
        version = match.group(1)
        files = sources(root)
        stage_parent = args.export_dir.resolve().parent if args.export_dir else root / 'downloads'
        stage_parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.aiphonekvm-source-', dir=stage_parent) as temporary:
            stage = Path(temporary) / 'source'
            stage.mkdir()
            manifest = {}
            for name, path in files.items():
                destination = stage / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                data = path.read_bytes()
                destination.write_bytes(data)
                destination.chmod(stat.S_IMODE(path.stat().st_mode) & 0o777)
                manifest[name] = sha(data)
            (stage / 'SOURCE-MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n')
            report = scan_tree(stage, rules)
            if report['findings']:
                print(json.dumps({'status': 'privacy_check_failed', **report}, ensure_ascii=False, indent=2))
                return 1
            output_dir = root / 'downloads'
            output_dir.mkdir(exist_ok=True)
            output = output_dir / f'AiPhoneKVM-{version}-source.zip'
            if output.is_symlink():
                raise ValueError('Archive output is a symbolic link')
            temporary_zip = Path(temporary) / 'source.zip'
            prefix = f'AiPhoneKVM-{version}-source/'
            with zipfile.ZipFile(temporary_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
                for path in sorted(stage.rglob('*')):
                    if not path.is_file():
                        continue
                    name = path.relative_to(stage).as_posix()
                    info = zipfile.ZipInfo(prefix + name, date_time=(2000, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = (stat.S_IFREG | stat.S_IMODE(path.stat().st_mode)) << 16
                    archive.writestr(info, path.read_bytes(), compresslevel=9)
            with zipfile.ZipFile(temporary_zip) as archive:
                if archive.testzip() is not None or any(sha(archive.read(prefix + name)) != digest for name, digest in manifest.items()):
                    raise ValueError('Corresponding-source archive manifest verification failed')
            if args.export_dir:
                # Recheck immediately before rename; an intervening nonempty directory is never overwritten.
                check_destination(args.export_dir, root)
                os.rename(stage, args.export_dir.absolute())
            os.replace(temporary_zip, output)
        result = {'archive': str(output), 'source_files': len(files), 'bytes': output.stat().st_size,
                  'sha256': sha(output.read_bytes()), 'manifest_matches': True,
                  'privacy_check_passed': True, 'git_history_included': False,
                  'runtime_data_and_credentials_excluded': True}
        if args.export_dir:
            result['export_dir'] = str(args.export_dir.resolve())
        evidence = root / 'evidence'
        evidence.mkdir(exist_ok=True)
        (evidence / 'source-verification.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (OSError, ValueError, zipfile.BadZipFile):
        print('Source export failed: unsafe/nonempty destination, missing source, or unreadable input; private contents were not logged', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
