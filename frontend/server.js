const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 4200;
const BACKEND_TARGET = { host: '127.0.0.1', port: 8088 };

const server = http.createServer((req, res) => {
  // CORS & Tunnel Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, PATCH, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Bypass-Tunnel-Remainder', 'true');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    return res.end();
  }

  // Proxy /api/v1 requests to Spring Boot backend on port 8088
  if (req.url.startsWith('/api')) {
    const proxyReq = http.request({
      host: BACKEND_TARGET.host,
      port: BACKEND_TARGET.port,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: '127.0.0.1:8088' }
    }, (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers);
      proxyRes.pipe(res, { end: true });
    });

    proxyReq.on('error', (err) => {
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Backend gateway connection failed', details: err.message }));
    });

    req.pipe(proxyReq, { end: true });
    return;
  }

  // Serve static files from frontend directory
  let cleanUrl = req.url.split('?')[0];
  if (cleanUrl === '/') cleanUrl = '/index.html';
  let filePath = path.normalize(path.join(__dirname, cleanUrl));
  const ext = path.extname(filePath).toLowerCase();

  const mimeTypes = {
    '.html': 'text/html',
    '.js': 'text/javascript',
    '.css': 'text/css',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpg',
    '.svg': 'image/svg+xml'
  };

  const contentType = mimeTypes[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, content) => {
    if (err) {
      fs.readFile(path.join(__dirname, 'index.html'), (err2, fallback) => {
        if (err2) {
          res.writeHead(404);
          res.end('File Not Found');
        } else {
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end(fallback, 'utf-8');
        }
      });
    } else {
      res.writeHead(200, { 'Content-Type': contentType });
      res.end(content, 'utf-8');
    }
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Unified Frontend & API Proxy Server running on port ${PORT}`);
});
