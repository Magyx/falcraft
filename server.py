# Minimal local HTTP server for Falcraft.
# This server mimics the fal.ai queue API with stubbed responses for offline testing.

import base64
import json
import os
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse

_ONE_BY_ONE_PNG = (
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMBA6X3Kl0AAAAASUVORK5CYII='
)

_DUMMY_GLB = base64.b64encode(b'glTF' + bytes([2]) + bytes(7)).decode()

class FalcraftRequestHandler(BaseHTTPRequestHandler):
    def _send_json(self, payload, status=200):
        data = json.dumps(payload).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        _ = self.rfile.read(content_length)
        path = urlparse(self.path).path

        if path == '/z-image/turbo':
            self._send_json({
                'request_id': '1',
                'response_url': 'http://localhost:8000/z-image/turbo/1/result',
                'status_url': 'http://localhost:8000/z-image/turbo/1/status'
            })
            return
        if path == '/meshy/v6-preview/text-to-3d':
            self._send_json({
                'request_id': '1',
                'response_url': 'http://localhost:8000/meshy/v6-preview/text-to-3d/1/result',
                'status_url': 'http://localhost:8000/meshy/v6-preview/text-to-3d/1/status'
            })
            return
        if path == '/sam-3/3d-objects':
            self._send_json({
                'request_id': '1',
                'response_url': 'http://localhost:8000/sam-3/3d-objects/1/result',
                'status_url': 'http://localhost:8000/sam-3/3d-objects/1/status'
            })
            return
        if path == '/nano-banana/edit':
            self._send_json({
                'request_id': '1',
                'response_url': 'http://localhost:8000/nano-banana/edit/1/result',
                'status_url': 'http://localhost:8000/nano-banana/edit/1/status'
            })
            return
        if path == '/openrouter/router/vision':
            self._send_json({
                'request_id': '1',
                'response_url': 'http://localhost:8000/openrouter/router/vision/1/result',
                'status_url': 'http://localhost:8000/openrouter/router/vision/1/status'
            })
            return
        self._send_json({'error': 'Unknown endpoint'}, status=404)

    def do_GET(self):
        path = urlparse(self.path).path
        if path.endswith('/status'):
            self._send_json({'status': 'COMPLETED'})
            return
        if path == '/z-image/turbo/1/result' or path == '/nano-banana/edit/1/result':
            self._send_json({'images': [{'url': 'data:image/png;base64,' + _ONE_BY_ONE_PNG}]})
            return
        if path == '/meshy/v6-preview/text-to-3d/1/result' or path == '/sam-3/3d-objects/1/result':
            self._send_json({'glb': 'data:model/gltf-binary;base64,' + _DUMMY_GLB, 'texture_url': None})
            return
        if path == '/openrouter/router/vision/1/result':
            self._send_json({'choices': [{'message': {'content': 'This is a stub vision response.'}}]})
            return
        self._send_json({'error': 'Not found'}, status=404)


def run_server(port: int = 8000) -> None:
    server_address = ('', port)
    httpd = HTTPServer(server_address, FalcraftRequestHandler)
    print(f'Serving Falcraft local API on port {port}...')
    httpd.serve_forever()


if __name__ == '__main__':
    port = int(os.environ.get('PORT', '8000'))
    run_server(port)
