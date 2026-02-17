FROM node:22-slim

RUN apt-get update -y && apt-get install -y openssl git && rm -rf /var/lib/apt/lists/*

# Install OpenClaw globally (official method)
RUN npm install -g openclaw@latest

# Create openclaw config directory
RUN mkdir -p /home/node/.openclaw && chown -R node:node /home/node

WORKDIR /home/node

USER node

EXPOSE 4200

# Start the OpenClaw gateway daemon
CMD ["openclaw", "--yes"]
