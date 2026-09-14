FROM node:20-alpine

WORKDIR /app

COPY package.json ./
COPY loadgen.js ./

CMD ["node", "loadgen.js"]
