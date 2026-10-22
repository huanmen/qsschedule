"""Run as root over SSH, then restart qsschedule-portal to revoke sessions."""
import getpass,hashlib,json,os,secrets,shutil
from pathlib import Path

def main():
    password=getpass.getpass('New password (at least 16 characters): ')
    if len(password)<16:raise SystemExit('Password must contain at least 16 characters')
    if password!=getpass.getpass('Confirm new password: '):raise SystemExit('Passwords differ')
    target=Path('/etc/qsschedule/portal-auth.json')
    salt=secrets.token_hex(16)
    data=dict(salt=salt,iterations=310000,hash=hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),310000).hex())
    temporary=target.with_suffix('.new')
    fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as stream:json.dump(data,stream)
    shutil.chown(temporary,user='root',group='qsschedule');temporary.chmod(0o640)
    temporary.replace(target)
    print('Password saved. Run: systemctl restart qsschedule-portal')

if __name__=='__main__':main()
