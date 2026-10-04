"""One-shot raw TCP sender for a single connected ADB reverse target. Binds loopback only."""
import socket,sys,pathlib,time
path=pathlib.Path(sys.argv[1]).resolve(); host='127.0.0.1'; port=8130
with socket.socket() as server:
 server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); server.bind((host,port)); server.listen(1)
 conn,addr=server.accept()
 with conn, path.open('rb') as source:
  conn.settimeout(30); request=b''
  while b'\r\n\r\n' not in request and len(request)<8192: request+=conn.recv(4096)
  if not request.startswith(b'GET '): raise RuntimeError('Expected an HTTP GET')
  conn.sendall(f'HTTP/1.1 200 OK\r\nContent-Length: {path.stat().st_size}\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n'.encode('ascii'))
  started=time.monotonic(); count=0
  while True:
   block=source.read(256*1024)
   if not block: break
   conn.sendall(block); count+=len(block)
  print(f'{path.name}: {count} bytes in {time.monotonic()-started:.2f}s from {addr}',flush=True)
