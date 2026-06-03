FROM node:20-alpine

WORKDIR /app

# Install all dependencies so the development entrypoint can use ts-node-dev.
COPY package.json package-lock.json* ./
RUN npm install

# Copy source
COPY . ./

EXPOSE 3000

# Default: run dev server for local development
CMD ["npm", "run", "dev"]
