#!/bin/bash
# RustDesk 专属私有服务端一键部署脚本 (适用于 Ubuntu / Debian / CentOS)

set -e

echo "=== 开始部署 RustDesk 私有信令与中继服务 ==="

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "未检测到 Docker，正在自动安装 Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable --now docker
fi

if ! command -v docker-compose &> /dev/null; then
    echo "正在安装 docker-compose..."
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi

# 获取公网 IP
PUBLIC_IP=$(curl -s4 ifconfig.me || curl -s4 icanhazip.com || echo "你的公网IP")
echo "检测到服务器公网 IP 为: ${PUBLIC_IP}"

# 创建工作目录
mkdir -p /opt/rustdesk-server/data
cd /opt/rustdesk-server

# 生成 docker-compose.yml
cat <<EOF > docker-compose.yml
version: '3'
networks:
  rustdesk-net:
    external: false

services:
  hbbs:
    container_name: hbbs
    ports:
      - 21115:21115
      - 21116:21116
      - 21116:21116/udp
      - 21118:21118
    image: rustdesk/rustdesk-server:latest
    command: hbbs -r ${PUBLIC_IP}:21117 -k _
    volumes:
      - ./data:/root
    networks:
      - rustdesk-net
    depends_on:
      - hbbr
    restart: unless-stopped

  hbbr:
    container_name: hbbr
    ports:
      - 21117:21117
      - 21119:21119
    image: rustdesk/rustdesk-server:latest
    command: hbbr -k _
    volumes:
      - ./data:/root
    networks:
      - rustdesk-net
    restart: unless-stopped
EOF

echo "正在启动容器..."
docker-compose up -d

# 等待生成公钥
sleep 3

echo ""
echo "========================================================"
echo "🎉 部署成功！你的专属私有 RustDesk 服务器信息如下："
echo "========================================================"
echo "ID 服务器 (ID Server)     : ${PUBLIC_IP}:21116"
echo "中继服务器 (Relay Server) : ${PUBLIC_IP}:21117"
echo "API 服务器 (API Server)   : ${PUBLIC_IP}:21116"
if [ -f "/opt/rustdesk-server/data/id_ed25519.pub" ]; then
    PUB_KEY=$(cat /opt/rustdesk-server/data/id_ed25519.pub)
    echo "公钥 (Key)                : ${PUB_KEY}"
else
    echo "公钥 (Key)                : 请稍后查看 /opt/rustdesk-server/data/id_ed25519.pub"
fi
echo "========================================================"
echo "请在云服务器安全组/防火墙开放以下端口：21115-21119 (TCP) 以及 21116 (UDP)"
