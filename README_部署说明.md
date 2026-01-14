# 后端部署说明

## 快速部署

**直接在文件资源管理器中双击运行 `部署.bat` 即可！**

这个批处理文件会自动：
1. 上传后端源码到服务器
2. 在服务器上编译打包
3. 停止旧服务并启动新服务
4. 验证部署是否成功

## 手动部署步骤（如果批处理文件无法运行）

### 步骤1: 上传源码

在 PowerShell 或 CMD 中执行：

```bash
scp -i "dl.pem" -o IdentitiesOnly=yes -o PubkeyAcceptedAlgorithms=+ssh-rsa -o HostkeyAlgorithms=+ssh-rsa -r "server_backend_src\backend\*" ubuntu@124.223.198.83:/home/ubuntu/backend/
```

### 步骤2: 连接到服务器并部署

```bash
ssh -i "dl.pem" -o IdentitiesOnly=yes -o PubkeyAcceptedAlgorithms=+ssh-rsa -o HostkeyAlgorithms=+ssh-rsa ubuntu@124.223.198.83
```

然后在服务器上执行：

```bash
cd /home/ubuntu/backend
mvn clean package -DskipTests -q
fuser -k 8080/tcp 2>/dev/null
sleep 2
cp target/wms-simple-1.0.0.jar /opt/app/wms/backend/target/
cd /opt/app/wms/backend/target
nohup java -Xms256m -Xmx512m -jar wms-simple-1.0.0.jar > /opt/app/wms/logs/app.log 2>&1 &
sleep 12
curl -s http://localhost:8080/api/health
```

## 验证部署

```bash
curl -s http://124.223.198.83:8080/api/health
```

## 查看日志

```bash
ssh -i "dl.pem" -o IdentitiesOnly=yes ubuntu@124.223.198.83 "tail -f /opt/app/wms/logs/app.log"
```








