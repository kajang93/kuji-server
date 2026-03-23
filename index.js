import express from "express";
const app = express();

app.get("/", (req, res) => {
    res.send("Hello from Kuji server 🚀");
});

const PORT = 8080;
const HOST = '0.0.0.0'; // 여기에 실제 IP(예: 192.168.0.x)를 적으셔도 됩니다. '0.0.0.0'은 모든 IP에서의 접속을 허용합니다.

app.listen(PORT, HOST, () => {
    console.log(`✅ Server running on http://${HOST}:${PORT}`);
});
