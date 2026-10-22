import { createServer } from "node:http";

// 仅供隔离浏览器验收：矩形是合成几何，不代表真实行政边界，也不提供业务统计。
const regions = [
    { adcode: 440000, x: 110, y: 22 },
    { adcode: 330000, x: 118, y: 28 },
    { adcode: 370000, x: 116, y: 35 },
];
const body = JSON.stringify({
    type: "FeatureCollection",
    features: regions.map(({ adcode, x, y }) => ({
        type: "Feature",
        properties: { adcode },
        geometry: {
            type: "Polygon",
            coordinates: [
                [
                    [x, y],
                    [x + 3, y],
                    [x + 3, y + 2],
                    [x, y + 2],
                    [x, y],
                ],
            ],
        },
    })),
});

const server = createServer((request, response) => {
    if (request.method === "GET" && request.url === "/health") {
        response.writeHead(200).end("ok");
    } else if (
        request.method === "GET" &&
        request.url === "/100000_full.json"
    ) {
        response
            .writeHead(200, { "Content-Type": "application/json" })
            .end(body);
    } else {
        response.writeHead(404).end();
    }
});
server.listen(18081, "127.0.0.1");
for (const signal of ["SIGTERM", "SIGINT"])
    process.on(signal, () => server.close());
