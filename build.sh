#!/usr/bin/env sh
# HotelsX 一键打包（Linux / macOS）
cd "$(dirname "$0")" || exit 1

echo
echo "=========================================================="
echo "  HotelsX 一键打包"
echo "=========================================================="
echo

if [ ! -f ./mvnw ]; then
  echo "[错误] 找不到 mvnw，请确认本脚本位于项目根目录。"
  exit 1
fi

# ---------- 1. 定位 java：优先 JAVA_HOME，其次 PATH ----------
JAVA_EXE=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_EXE="$(command -v java)"
fi

if [ -z "$JAVA_EXE" ]; then
  echo "[错误] 没有找到 Java。"
  echo
  echo "  本项目需要 JDK 21 或更高版本，请先安装："
  echo "    https://adoptium.net/temurin/releases/?version=21"
  echo
  echo "  安装后二选一："
  echo "    1) 把 JAVA_HOME 环境变量指向 JDK 安装目录"
  echo "    2) 把 JDK 的 bin 目录加入 PATH"
  echo
  exit 1
fi

# ---------- 2. 校验 JDK 主版本号 >= 21 ----------
JAVA_VER="$("$JAVA_EXE" -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1)"
JAVA_MAJOR="$(echo "$JAVA_VER" | cut -d. -f1)"
case "$JAVA_MAJOR" in
  1) JAVA_MAJOR=8 ;;
  '' | *[!0-9]*) JAVA_MAJOR=0 ;;
esac

if [ "$JAVA_MAJOR" -lt 21 ]; then
  echo "[错误] 检测到 Java $JAVA_VER，本项目需要 JDK 21 或更高版本。"
  echo "        当前使用的 java: $JAVA_EXE"
  echo
  echo "        如果你已经装了新版 JDK 仍然报这个错，说明 PATH 上的 java 是旧版本，"
  echo "        请把 JAVA_HOME 指向新的 JDK 目录后重试。"
  echo
  exit 1
fi

echo "[1/2] 环境检查通过：Java $JAVA_VER"
echo "[2/2] 开始构建（首次运行会自动下载 Maven 和依赖，请耐心等待）"
echo

if ! ./mvnw -B clean package -DskipTests; then
  echo
  echo "=========================================================="
  echo "  构建失败，请把上面的报错信息发给开发者。"
  echo "=========================================================="
  echo
  exit 1
fi

echo
echo "=========================================================="
echo "  构建成功"
for f in target/HotelsX-*.jar; do
  [ -f "$f" ] && echo "  插件位置: $(cd "$(dirname "$f")" && pwd)/$(basename "$f")"
done
echo
echo "  把它放进服务器的 plugins 目录，重启服务器即可。"
echo "=========================================================="
echo
