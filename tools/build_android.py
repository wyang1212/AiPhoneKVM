"""Build with an isolated Gradle home, avoiding this host's global repository init script."""
from pathlib import Path
import os
import subprocess
import sys
from urllib.parse import urlparse

workspace = Path(__file__).resolve().parent.parent
project = workspace / 'src'
(workspace / 'evidence').mkdir(exist_ok=True)
host_gradle = Path.home() / '.gradle'
gradle_home = workspace.parent / '.build' / 'gradle-home'
gradle_home.mkdir(parents=True, exist_ok=True)
# Share only normal downloaded caches, never the host's init scripts or user properties.
cache = gradle_home / 'caches'
if not cache.exists():
    if (host_gradle / 'caches').is_dir():
        cache.symlink_to(host_gradle / 'caches', target_is_directory=True)
    else:
        cache.mkdir()
env = os.environ.copy()
env['GRADLE_USER_HOME'] = str(gradle_home)
jvms = list((host_gradle / 'jdks').glob('*21*/bin/java'))
if jvms:
    env['JAVA_HOME'] = str(jvms[0].parent.parent)
distributions = list((host_gradle / 'wrapper' / 'dists' / 'gradle-9.5.0-bin').glob('*/gradle-9.5.0/bin/gradle'))
launcher = str(distributions[0]) if distributions else './gradlew'
args = [launcher, '--no-daemon', '--console=plain', '--max-workers=4']
for scheme in ('http', 'https'):
    value = env.get(scheme.upper() + '_PROXY') or env.get(scheme + '_proxy')
    if value:
        proxy = urlparse(value)
        if proxy.username or proxy.password:
            raise SystemExit('Authenticated proxy needs separate local configuration')
        args += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}']
args += sys.argv[1:] or [':app:assembleDefaultDebug', ':app:testDefaultDebugUnitTest', ':app:lintDefaultDebug', ':capture:assembleDebug', ':capture:testDebugUnitTest', ':capture:lintDebug']
local_properties = project / 'local.properties'
local_lines = local_properties.read_text().splitlines() if local_properties.exists() else []
sdk_line = f'sdk.dir={env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT") or Path.home() / "Android" / "Sdk"}'
local_lines = [line for line in local_lines if not line.strip().startswith('sdk.dir=')]
local_properties.write_text('\n'.join([*local_lines, sdk_line]) + '\n')
with (workspace / 'evidence' / 'build.log').open('w') as log:
    result = subprocess.run(args, cwd=project, env=env, stdout=log, stderr=subprocess.STDOUT)
print(f'Gradle exit: {result.returncode}; evidence/build.log')
raise SystemExit(result.returncode)
