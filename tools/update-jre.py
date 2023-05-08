import urllib.parse
import urllib.request
import json

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
    req.add_header('User-agent', 'Mozilla/5.0') # api seems to block urllib ua

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

fetch_jre('WIN64_', '11.0.19+7', 'x64', 'windows')
fetch_jre('WIN32_', '11.0.19+7', 'x86', 'windows')
fetch_jre('MAC_AMD64_', '11.0.19+7', 'x64', 'mac')
fetch_jre('MAC_AARCH64_', '17.0.7+7', 'aarch64', 'mac')
fetch_jre('LINUX_AMD64_', '11.0.19+7', 'x64', 'linux')
fetch_jre('LINUX_AARCH64_', '11.0.19+7', 'aarch64', 'linux')