"""Download pinned developer-only PostgreSQL; never installed as a system service."""
from pathlib import Path
import hashlib, tarfile, urllib.request, zipfile

root=Path(__file__).resolve().parent.parent/'.tools/postgres'
root.mkdir(parents=True,exist_ok=True)
version='17.10.0'
url=f'https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64/{version}/embedded-postgres-binaries-windows-amd64-{version}.jar'
archive=root/'postgres.jar'
if not archive.exists():urllib.request.urlretrieve(url,archive)
expected=urllib.request.urlopen(url+'.sha256',timeout=30).read().decode().strip().split()[0]
if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected:raise SystemExit('PostgreSQL checksum mismatch; not extracted.')
with zipfile.ZipFile(archive) as jar:
    packed=root/'postgres-windows-x86_64.txz';packed.write_bytes(jar.read('postgres-windows-x86_64.txz'))
    with tarfile.open(packed) as tar:tar.extractall(root/'bin-dist',filter='data')
print('PostgreSQL developer test binary ready; run python scripts/test_database.py')
