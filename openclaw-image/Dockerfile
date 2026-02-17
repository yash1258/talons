FROM node:22-slim

RUN apt-get update -y && apt-get install -y openssl git && rm -rf /var/lib/apt/lists/*

# Set up local prefix for node user to avoid permission issues with /usr/local
ENV NPM_CONFIG_PREFIX=/home/node/.npm-global
ENV PATH=$PATH:/home/node/.npm-global/bin

RUN mkdir -p /home/node/.npm-global && chown -R node:node /home/node

WORKDIR /home/node

USER node

# Install OpenClaw globally in the node-owned prefix
RUN npm install -g openclaw@latest

# Create openclaw config directory
RUN mkdir -p /home/node/.openclaw

EXPOSE 4200

# Start the OpenClaw gateway daemon
CMD ["openclaw", "--yes"]
