FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/nginx.conf
RUN mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp && chown -R nginx:nginx /var/cache/nginx /var/run
USER nginx
EXPOSE 80
