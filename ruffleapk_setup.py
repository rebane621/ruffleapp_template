#!/usr/bin/env python3

import os.path
import re
import shutil
from pathlib import Path
import datetime
import subprocess


def readProperties():
    props = {}
    with open('ruffleapk.properties', 'r') as f:
        for line in f:
            if ':' in line and not line.startswith('#'):
                name, value = line.split(':', 1)
                props[name.strip().upper()] = value.strip()
    return props


def patchFile(file, patches):
    print(f"-- Processing {file} ...")
    lines = []
    with open(file, 'r') as f:
        for line in f:
            for pattern,replacement in patches:
                if isinstance(pattern, re.Pattern):
                    line = pattern.sub(replacement, line)
                else:
                    line = line.replace(pattern, replacement)
            lines.append(line.rstrip())
    with open(file, 'w') as f:
        for line in lines:
            f.write(line+"\n")


def patchWith(properties):
    print(f"Patching RuffleAPK for: {properties['APP_NAME']}")
    SWF_BASENAME = os.path.basename(properties['SWF_FILE'])
    
    patchFile('app/src/main/assets/www/index.html', [
        # patch assets/www/index.html <title>{APP_NAME}</title>
        (re.compile(r"<title>.*</title>"), f"<title>{properties['APP_NAME']}</title>"),
        # patch assets/www/index.html window.swfplayer.load("{SWF_FILE}");
        (re.compile(r"window\.swfplayer\.load\(\".*\"\)"), f"window.swfplayer.load(\"{SWF_BASENAME}\")")
    ])
    
    # copy to assets/www/{SWF_FILE}  (remove old swf files)
    print(f"-- Copying app/src/main/assets/www/{SWF_BASENAME} ...")
    for filename in os.listdir('app/src/main/assets/www'):
        if filename.endswith('.swf'):
            os.remove('app/src/main/assets/www/'+filename)
    shutil.copy2(properties['SWF_FILE'], f"app/src/main/assets/www/{SWF_BASENAME}");
    
    patchFile('app/src/main/res/values/strings.xml', [
        # patch res/values/strings.xml <string name="app_name">{APP_NAME}</string>
        (re.compile(r"<string name=\"app_name\">.*</string>"), f"<string name=\"app_name\">{properties['APP_NAME']}</string>"),
    ])
    
    # patch java package to {APP_PACKAGE}
    javabase = Path('app/src/main/java')
    oldpath = list(javabase.rglob("MainActivity.java"))[0].parent
    newpath = javabase / '/'.join(properties['APP_PACKAGE'].split('.'))
    if not oldpath == newpath:
        newpath.mkdir(0o777, True, True)  # make parents, existing is ok
        for file in os.listdir(oldpath):
            shutil.move(oldpath / file, newpath)
    
        # get existing package
        oldpackage = '.'.join(oldpath.relative_to(javabase).parts)
        for file in os.listdir(newpath):
            patchFile(newpath / file, [(oldpackage, properties['APP_PACKAGE'])])
    
    code = datetime.datetime.today().strftime('%Y%m%d%H')  # for uint32 this holds until the year 2147
    patchFile('build.gradle.kts', [
        # patch build.gradle.kts namespace = "{APP_PACKAGE}"
        (re.compile(r"\bnamespace *= *\".*\""), f"namespace = \"{properties['APP_PACKAGE']}\""),
        # patch build.gradle.kts applicationId = "{APP_PACKAGE}"
        (re.compile(r"\bapplicationId *= *\".*\""), f"applicationId = \"{properties['APP_PACKAGE']}\""),
        # patch build.gradle.kts versionCode = 1
        (re.compile(r"\bversionCode *= *[0-9]+"), f"versionCode = \"{code}\""),
        # patch build.gradle.kts versionName = "1.0"
        (re.compile(r"\bversionName *= *\".*\""), f"versionName = \"{properties['APP_VERSION']}\""),
    ])
    patchFile('settings.gradle.kts', [
        # patch settings.gradle.kts rootProject.name = "{APP_NAME}"
        (re.compile(r"\brootProject.name *= *\".*\""), f"rootProject.name = \"{properties['APP_NAME']}\""),
    ])
    # TODO patch images


def invokeBuild():
    print("-- Building APK image ...")
    ret = subprocess.run(["gradlew", "clean", "build"], shell=True)
    if ret == 0:
        shutil.copy2('app/build/outputs/apk/release/app-release-unsigned.apk', './ruffleapp.apk')


def main():
    properties = readProperties()
    if not 'APP_NAME' in properties:
        print('APP_NAME is not set')
        exit()
    if not 'APP_PACKAGE' in properties:
        print('APP_PACKAGE is not set')
        exit()
    if not re.compile(r"[a-zA-Z]\w+(\.[a-zA-Z]\w+)+").fullmatch(properties['APP_PACKAGE']):
        print('APP_PACKAGE needs to be a unique reverse-domain-ish')
        exit()
    if not 'APP_VERSION' in properties:
        print('APP_VERSION is not set')
        exit()
    if not re.compile(r"[0-9]+\.[0-9]+(\.[0-9]+)?").fullmatch(properties['APP_VERSION']):
        print('APP_VERSION needs to be X.Y.Z')
        exit()
    if not 'SWF_FILE' in properties:
        print('SWF_FILE is not set')
        exit()
    if not os.path.isfile(properties['SWF_FILE']):
        print(f"SWF_FILE {properties['SWF_FILE']} does not exist, check your path")
        exit()
    if 'LOGO_FILE' in properties and not os.path.isfile(properties['LOGO_FILE']):
        print(f"LOGO_FILE {properties['LOGO_FILE']} is set but does not exist")
        exit()
    print("NOTE: Logos are currently not processed")
    patchWith(properties)
    invokeBuild()


if __name__ == "__main__":
    main()