# Linux 环境资源（proot 完整发行版）

本目录存放 proot 完整 Linux 运行时的可选离线资源。全部可选——运行时优先从
本目录读取,缺失时改为联网下载（rootfs 在 App 内「Linux 环境」设置页配置 URL）。

## 资源清单

| 文件 | 说明 | 必需 |
| --- | --- | --- |
| `proot` | proot 可执行文件（arm64 ELF）。推荐 Termux 的 bionic 版（与本项目 jniLibs 中 libnode.so 同一构建体系,可直接在 Android 上运行）,或静态编译版。 | 推荐 |
| `talloc/libtalloc.so.2` | proot 动态链接依赖库,仅 Termux bionic 动态版需要。静态编译版可省略。 | 条件性 |
| `rootfs.tar.gz` | 离线 rootfs 归档（Debian/Ubuntu arm64,含 `/bin/bash`）。可选,不提供则联网下载。 | 可选 |

## 从 Termux 官方仓库获取 proot

Termux 的 proot 包构建产物可通过其官方 apt 仓库获取（arm64）：

- 仓库主页：https://packages.termux.dev/apt/termux-main/
- 包名：`proot`（依赖 `libtalloc`）

在 Linux PC 上：

```bash
# 以 aarch64 为例,下载 proot 与 libtalloc 的 .deb
# .deb 是 ar 归档,可解出 data.tar.xz,再解出 usr/bin/proot 与 usr/lib/libtalloc.so.2
# 将 proot 放到本目录,libtalloc.so.2 放到 talloc/ 子目录
```

另一种方式：用 proot-me/proot 官方源码交叉编译静态版（无动态依赖,单文件即用）。

## rootfs 官方下载源示例

App 内置默认源为 Ubuntu base arm64（`jammy`/`noble` release,官方 cdimage）：
`https://cdimage.ubuntu.com/ubuntu-base/releases/<release>/release/ubuntu-base-<版本>-base-arm64.tar.gz`

要点：rootfs 必须是 arm64、内含 `/bin/bash`,解压后 `bin/bash` 应位于归档顶层
（或带一层小目录,运行时自动提升）。