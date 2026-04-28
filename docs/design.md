# 百万级消息推送系统架构设计

## 概述

本文档从 P7 视角系统性梳理百万级消息推送系统的各层级参考指标和配置，基于数据量自顶向下推导各层设计。

### 核心数据量估算

假设：**100万在线用户**，每个用户**每秒1条消息**，平均消息大小 **1KB**

```
总吞吐量 = 1,000,000 msgs/s × 1KB = 1 GB/s ≈ 8 Gbps
峰值系数 = 3-5倍（突发流量）
峰值带宽 = 3-5 GB/s ≈ 24-40 Gbps
```

---

## 一、网卡层 (Network Card)

### 参考指标

| 指标 | 常规 | 高性能 | 百万级推荐 |
|------|------|--------|-----------|
| 网卡规格 | 1Gbps | 10Gbps | 25Gbps/100Gbps |
| 单卡吞吐 | 120MB/s | 1.2GB/s | 3GB/s |
| PPS能力 | 100K | 1M | 10M+ |
| 多队列 | 2-4 | 8-16 | 32+ |

### 百万级配置

```
单服务器网卡：25Gbps 双网卡绑定 (Bonding)
单网卡带宽：25Gbps ≈ 3.125GB/s
服务器数量：峰值5GB/s ÷ 3.125GB/s ≈ 2台（冗余）
实际配置：4台 25Gbps 网卡服务器（2+2冗余）
```

### 关键配置

```bash
# 启用多队列
ethtool -L eth0 combined 32

# 开启网卡多队列
set_irq_affinity.sh eth0

# 调整中断合并 (降低延迟)
ethtool -C eth0 rx-usecs 50 tx-usecs 50

# 启用 jumbo frame (大帧)
ifconfig eth0 mtu 9000
```

---

## 二、网络层 (Network)

### 参考指标

| 指标 | 常规 | 百万级要求 |
|------|------|-----------|
| 带宽 | 1Gbps | 40Gbps+ 出口 |
| 延迟 | 10-50ms | <5ms 同城 |
| 丢包率 | <1% | <0.1% |
| BGP线路 | 单线 | 多线BGP |

### 网络架构设计

```
用户 → CDN边缘节点 → L4/L7负载均衡 → 应用服务器
         ↓                              ↓
      静态资源                      消息队列/Redis
```

### 关键配置

**Nginx 四层负载均衡 (SkA)**

```nginx
stream {
    upstream backend {
        least_conn;
        server 10.0.1.1:8080 weight=5;
        server 10.0.1.2:8080 weight=5;
        keepalive 1024;
    }
    
    server {
        listen 80;
        proxy_pass backend;
        proxy_connect_timeout 1s;
        proxy_timeout 10s;
    }
}
```

**Nginx 七层负载均衡**

```nginx
http {
    upstream gateway {
        keepalive 512;
        server 10.0.1.1:8080;
        server 10.0.1.2:8080;
    }
    
    server {
        location /push {
            proxy_pass http://gateway;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_buffering off;
            chunked_transfer_encoding off;
        }
    }
}
```

---

## 三、操作系统层 (OS)

### 核心参数参考

| 参数 | 默认值 | 百万级推荐 | 说明 |
|------|--------|-----------|------|
| `fs.file-max` | 1804352 | 2000000 | 系统文件描述符上限 |
| `fs.nr_open` | 1048576 | 2000000 | 单进程FD上限 |
| `net.ipv4.tcp_max_syn_backlog` | 128 | 65535 | SYN队列长度 |
| `net.core.somaxconn` | 128 | 65535 | TCP连接队列 |
| `net.ipv4.tcp_tw_reuse` | 0 | 1 | 快速复用TIME_WAIT |
| `net.ipv4.tcp_fin_timeout` | 60 | 15 | FIN超时 |
| `net.ipv4.ip_local_port_range` | 32768-60999 | 1024-65535 | 端口范围 |
| `net.core.rmem_max` | 212992 | 16777216 | 接收缓冲区 |
| `net.core.wmem_max` | 212992 | 16777216 | 发送缓冲区 |
| `vm.max_map_count` | 65530 | 262144 | mmap上限 |

### 完整 sysctl 配置

```bash
# /etc/sysctl.conf

# 文件描述符 (现代Linux默认约180万，通常够用，可按需调整)
fs.file-max = 2000000
fs.nr_open = 2000000

# TCP连接优化
net.ipv4.tcp_max_syn_backlog = 65535
net.core.somaxconn = 65535
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_keepalive_time = 300
net.ipv4.tcp_keepalive_intvl = 30
net.ipv4.tcp_keepalive_probes = 5

# 网络缓冲区
net.core.rmem_default = 262144
net.core.rmem_max = 16777216
net.core.wmem_default = 262144
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216

# 连接跟踪
net.netfilter.nf_conntrack_max = 1048576
net.nf_conntrack_max = 1048576

# 内存映射
vm.max_map_count = 262144
vm.swappiness = 0

# 其他
net.core.netdev_max_backlog = 65535
net.ipv4.tcp_slow_start_after_idle = 0
```

### 用户级配置

```bash
# /etc/security/limits.conf
* soft nofile 2000000
* hard nofile 2000000
* soft nproc 65535
* hard nproc 65535
```

---

## 四、服务层 (Service)

### 百万级服务器估算

基于数据量推导：

```
峰值流量：5 GB/s (约40Gbps)
单服务器处理能力（4核8G）：
  - 纯推送：10K-50K msgs/s
  - 带业务逻辑：5K-20K msgs/s

需要的应用服务器：
  - 最低：1,000,000 ÷ 20,000 = 50台
  - 推荐（冗余）：50 × 1.5 = 75台
  - 实际生产：100台（预留扩容）
```

### 服务架构

```
                    ┌─────────────────────────────────────┐
                    │         CDN (静态资源)               │
                    └─────────────────────────────────────┘
                                       │
                    ┌─────────────────────────────────────┐
                    │    L4/L7 负载均衡 (Nginx/Envoy)       │
                    │    带宽：40Gbps+                     │
                    └─────────────────────────────────────┘
                                       │
           ┌───────────────────────────┼───────────────────────────┐
           │                           │                           │
           ▼                           ▼                           ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│   API Gateway    │    │   API Gateway   │    │   API Gateway   │
│   (Java/Go)      │    │   (Java/Go)     │    │   (Java/Go)     │
│   10.0.1.1       │    │   10.0.1.2      │    │   10.0.1.3      │
└──────────────────┘    └──────────────────┘    └──────────────────┘
           │                           │                           │
           └───────────────────────────┼───────────────────────────┘
                                       │
                    ┌─────────────────────────────────────┐
                    │         消息队列 (Kafka/RocketMQ)     │
                    │   峰值：5GB/s写入                     │
                    └─────────────────────────────────────┘
           │                           │                           │
           ▼                           ▼                           ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  Push Worker 1   │    │  Push Worker 2  │    │  Push Worker N   │
│  50K/s           │    │  50K/s          │    │  50K/s          │
└──────────────────┘    └──────────────────┘    └──────────────────┘
```

### 推荐配置

| 角色 | 数量 | 配置 | 说明 |
|------|------|------|------|
| 负载均衡 | 4+ | 8核16G, 25Gbps | Nginx/Envoy |
| API Gateway | 20+ | 4核8G | 无状态，可扩容 |
| 消息队列 | 9+ | 8核32G, 2TB SSD | Kafka集群 |
| Push Worker | 100+ | 4核8G | 消费队列，推送消息 |
| 管理后台 | 4+ | 4核8G | Web后台 |

---

## 五、组件层 (Component)

### Redis (推送通道)

| 指标 | 常规 | 百万级推荐 |
|------|------|-----------|
| 架构 | 单机/主从 | Cluster (6+ 节点) |
| 内存 | 8GB | 64GB × 6 |
| 吞吐量 | 100K/s | 1M+/s |
| 连接数 | 10K | 50K+ |

```yaml
# Redis Cluster 配置
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 15000
maxmemory 64gb
maxmemory-policy allkeys-lru
tcp-keepalive 300
```

**百万级数据预估**：
```
100万用户 × 1KB/用户（Token/状态）= 1GB 内存
加上消息缓存、限流数据：约 4-8GB
推荐：6节点 × 64GB = 384GB
```

### MySQL (消息存储)

| 指标 | 常规 | 百万级推荐 |
|------|------|-----------|
| 实例 | 单机 | 主从+分库 |
| 容量 | 100GB | 10TB+ |
| TPS | 1K | 10K+ |
| 连接数 | 500 | 2000 |

```sql
-- 分库分表策略 (按用户ID哈希)
CREATE TABLE push_message (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(256),
    content TEXT,
    created_at DATETIME,
    status TINYINT,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
/*!50500 PARTITION BY HASH (user_id % 256) PARTITIONS 256 */;
```

**百万级数据预估**：
```
消息存储：100万/天 × 1KB × 30天 = 30GB/月
加上索引、日志：约 100GB
推荐：MySQL Cluster + 读写分离
```

### 消息队列 (Kafka)

| 指标 | 常规 | 百万级推荐 |
|------|------|-----------|
| Broker | 3 | 9+ |
| 磁盘 | 1TB | 2TB × 9 |
| 副本 | 1 | 3 |
| 吞吐量 | 100MB/s | 1GB/s+ |

```properties
# Kafka 配置
bootstrap.servers=kafka1:9092,kafka2:9092,kafka3:9092
acks=all
retries=3
batch.size=16384
linger.ms=10
buffer.memory=33554432
compression.type=snappy
```

---

## 六、数据量串联设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    百万级消息推送数据流                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户请求 ──▶ 1KB ──▶ 网卡(40Gbps) ──▶ 负载均衡                   │
│                            │                                    │
│                            ▼                                    │
│                    100万QPS (1GB/s)                             │
│                            │                                    │
│                            ▼                                    │
│              API Gateway ──▶ 消息队列 (Kafka)                   │
│                   (20台)         │                              │
│                                  ▼                              │
│                        写入 1GB/s                                │
│                                  │                              │
│                                  ▼                              │
│              Push Worker ──▶ Redis 发布订阅                     │
│                  (100台)          │                             │
│                                  ▼                              │
│                           用户终端                               │
│                                  │                              │
│                                  ▼                              │
│                      推送带宽 1GB/s (出)                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 容量规划公式

```
应用服务器 = 峰值QPS ÷ 单机QPS × 冗余系数
Redis节点 = 数据量 ÷ 单节点内存 × 冗余系数  
Kafka分区 = 峰值吞吐量 ÷ 单分区吞吐 × 副本数
MySQL分片 = 数据量 ÷ 单库容量 × 读写分离系数
```

---

## 七、监控指标

| 层级 | 关键指标 | 告警阈值 |
|------|----------|----------|
| 网卡 | 带宽利用率 | >80% |
| 网络 | 延迟/PPS | >5ms / >500K |
| OS | CPU/FD/连接 | >80% / >80% |
| 服务 | QPS/响应时间 | >70%阈值 / >200ms |
| 组件 | CPU/内存/磁盘 | >80% |

---

## 八、设计总结

### 自顶向下的设计方法论

1. **明确业务指标**：100万在线用户，1msg/s/人，1KB/msg
2. **计算总吞吐量**：1GB/s = 8Gbps，峰值 24-40Gbps
3. **逐层分解**：
   - 网卡层：需要 25Gbps × 4台
   - 网络层：40Gbps 出口带宽
   - OS层：调整文件描述符、TCP参数
   - 服务层：100台 Push Worker
   - 组件层：Redis Cluster + Kafka 集群

### 关键设计原则

1. **容量冗余**：按峰值 3-5 倍预留
2. **无状态设计**：Gateway/Worker 可水平扩展
3. **异步化**：消息队列削峰填谷
4. **分层监控**：每层独立告警

### 成本估算参考

| 组件 | 数量 | 单价 | 月成本(万元) |
|------|------|------|-------------|
| 服务器(4核8G) | 150台 | 500/月 | 7.5 |
| Redis Cluster | 6节点 | 2000/月 | 1.2 |
| Kafka 集群 | 9节点 | 3000/月 | 2.7 |
| MySQL 集群 | 6节点 | 2500/月 | 1.5 |
| 带宽(40Gbps) | - | - | 10+ |
| **合计** | - | - | **25+** |

> 注：以上为估算值，实际成本取决于云厂商和部署方式
