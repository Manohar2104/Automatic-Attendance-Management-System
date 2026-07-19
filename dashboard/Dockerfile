FROM nginx:1.27-alpine

COPY dashboard/nginx.conf /etc/nginx/conf.d/default.conf
COPY server/app/static/dashboard.html /usr/share/nginx/html/index.html
