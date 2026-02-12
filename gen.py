import os
import base64, zlib
B=r"c:\Users\Umar Fadhil Ramadhan\OneDrive\Documents\ayakasir\app\src\main\java\com\ayakasir\app\core\data\repository"
os.makedirs(B,exist_ok=True)
def w(n,d):
  open(os.path.join(B,n),"w",newline="\n").write(zlib.decompress(base64.b64decode(d)).decode())
  print(n)
files = {}
