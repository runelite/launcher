import urllib.parse
import urllib.request
import json
import sys

ver11 = sys.argv[1]
ver17 = sys.argv[2]

def fetch_jre(prefix, version_range, arch, os):
    url = 'https://api.adoptium.net/v3/assets/version/' + urllib.parse.quote(version_range) + '?'
    params = {
        'architecture': arch,
        'heap_size': 'normal',
        'image_type': 'jre',
        'jvm_impl': 'hotspot',
        'os': os,
        'page': 0,
        'page_size': 1,
        'project': 'jdk',
        'release_type': 'ga',
        'vendor': 'eclipse'
    }

    req = urllib.request.Request(url + urllib.parse.urlencode(params))
    req.add_header('User-agent', 'RuneLite') # api seems to block urllib ua

    ctx = urllib.request.urlopen(req)

    release = json.loads(ctx.read())

    pkg = release[0]["binaries"][0]["package"]
    checksum = pkg["checksum"]
    link = pkg["link"]
    ver = release[0]["version_data"]["openjdk_version"]

    print("# " + os + " " + arch)
    print(prefix + "VERSION=" + ver)
    print(prefix + "CHKSUM=" + checksum)
    print(prefix + "LINK=" + link)

def fetch_microsoft_jre(prefix, version, arch, os):
    urlversion = version.split('+')[0] # build is not included in url
    url = 'https://aka.ms/download-jdk/microsoft-jdk-' + urlversion + '-' + os + '-' + arch + '.zip.sha256sum.txt'
    req = urllib.request.Request(url)
    ctx = urllib.request.urlopen(req)
    checksum = ctx.read().decode("utf-8")
    shasum = checksum.split(' ')[0]
    print("# " + os + " " + arch)
    print(prefix + "VERSION=" + version)
    print(prefix + "CHKSUM=" + shasum)
    print(prefix + "LINK=https://aka.ms/download-jdk/microsoft-jdk-" + urlversion + "-" + os + "-" + arch + ".zip")

fetch_jre('WIN64_', ver11, 'x64', 'windows')
fetch_jre('WIN32_', ver11, 'x86', 'windows')
fetch_microsoft_jre('WIN_AARCH64_', ver11, 'aarch64', 'windows')
fetch_jre('MAC_AMD64_', ver17, 'x64', 'mac')
fetch_jre('MAC_AARCH64_', ver17, 'aarch64', 'mac')
fetch_jre('LINUX_AMD64_', ver11, 'x64', 'linux')
fetch_jre('LINUX_AARCH64_', ver11, 'aarch64', 'linux')
