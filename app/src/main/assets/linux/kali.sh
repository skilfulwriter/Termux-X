#!/data/data/com.termux/files/usr/bin/bash -e

clear

echo "============================================="
echo "               系统信息检查脚本"
echo "============================================="
echo
echo "当前时间: $(date)"
echo
echo "=== 系统信息 ==="
echo "主机名: $(hostname)"
echo "操作系统: $(uname -s)"
echo "内核版本: $(uname -r)"
echo "架构: $(uname -m)"
echo
echo "=== 磁盘使用情况 ==="
df -h | grep -v tmpfs
echo
echo "=== 内存使用情况 ==="
free -h
echo
echo "=== 用户信息 ==="
echo "当前用户: $(whoami)"
echo "登录用户: $(who)"
echo
echo "=== 系统运行时间 ==="
uptime
echo
echo "系统信息检查完成！"
echo

read -p "是否要下载并安装Kali NetHunter？(y/n): " choice
case "$choice" in
    [Yy])
        echo -e "\n开始执行Kali NetHunter安装脚本...\n"
        ;;
    [Nn])
        echo -e "\n已选择停止运行，脚本退出。"
        exit 0
        ;;
    *)
        echo -e "\n输入无效，脚本退出。"
        exit 1
        ;;
esac

VERSION=20250525
BASE_URL=https://kali.download/nethunter-images/current/rootfs
USERNAME=kali

log() {
    current_time=$(date +'%H:%M:%S')
    case "$1" in
        "INFO")
            echo -e "${blue_bg}[$current_time]${reset} ${blue_fg_strong}[信息]${reset} $2"
            ;;
        "WARN")
            echo -e "${yellow_bg}[$current_time]${reset} ${yellow_fg_strong}[警告]${reset} $2"
            ;;
        "ERROR")
            echo -e "${red_bg}[$current_time]${reset} ${red_fg_strong}[错误]${reset} $2"
            ;;
        *)
            echo -e "${blue_bg}[$current_time]${reset} ${blue_fg_strong}[调试]${reset} $2"
            ;;
    esac
}

error_exit() {
    log "ERROR" "$1"
    exit 1
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

function unsupported_arch() {
    error_exit "不支持的架构: $(getprop ro.product.cpu.abi)"
}

function ask() {
    while true; do
        if [ "${2:-}" = "Y" ]; then
            prompt="Y/n"
            default=Y
        elif [ "${2:-}" = "N" ]; then
            prompt="y/N"
            default=N
        else
            prompt="y/n"
            default=
        fi
        printf "${light_cyan}\n[?] "
        read -p "$1 [$prompt] " REPLY
        if [ -z "$REPLY" ]; then
            REPLY=$default
        fi
        printf "${reset}"
        case "$REPLY" in
            Y*|y*) return 0 ;;
            N*|n*) return 1 ;;
        esac
    done
}

function get_arch() {
    log "INFO" "检查设备架构..."
    case $(getprop ro.product.cpu.abi) in
        arm64-v8a)
            SYS_ARCH=arm64
            log "INFO" "检测到架构: ARM64"
            ;;
        armeabi|armeabi-v7a)
            SYS_ARCH=armhf
            log "INFO" "检测到架构: ARMhf"
            ;;
        *)
            unsupported_arch
            ;;
    esac
}

function set_strings() {
    local menu_options=()
    local descriptions=()
    
    if [[ ${SYS_ARCH} == "arm64" ]]; then
        menu_options=("full" "minimal" "nano")
        descriptions=("NetHunter ARM64 (完整版 - 2.1 GiB)" "NetHunter ARM64 (精简版 - 131.6 MiB)" "NetHunter ARM64 (纳米版 - 185.2 MiB)")
    elif [[ ${SYS_ARCH} == "armhf" ]]; then
        menu_options=("full" "minimal" "nano")
        descriptions=("NetHunter ARMhf (完整版 - 2.0 GiB)" "NetHunter ARMhf (精简版 - 122.2 MiB)" "NetHunter ARMhf (纳米版 - 174.2 MiB)")
    fi
    
    echo
    for i in "${!menu_options[@]}"; do
        printf "${green}[%d]${reset} %s\n" $((i+1)) "${descriptions[$i]}"
    done
    echo
    
    while true; do
        read -p "请选择要安装的镜像 (1-3，默认为1): " choice
        choice=${choice:-1}
        
        if [[ "$choice" =~ ^[1-3]$ ]]; then
            wimg="${menu_options[$((choice-1))]}"
            log "INFO" "选择了: ${descriptions[$((choice-1))]}"
            break
        else
            log "WARN" "请输入有效的选项 (1-3)"
        fi
    done
    CHROOT=kali-${SYS_ARCH}
    IMAGE_NAME=kali-nethunter-rootfs-${wimg}-${SYS_ARCH}.tar.xz
}

function prepare_fs() {
    unset KEEP_CHROOT
    if [ -d "${CHROOT}" ]; then
        if ask "找到现有的 rootfs 目录 (${CHROOT})。是否删除并创建新的？" "N"; then
            log "INFO" "删除现有的 rootfs 目录..."
            rm -rf "${CHROOT}" || error_exit "无法删除现有目录"
        else
            KEEP_CHROOT=1
            log "INFO" "保留现有的 rootfs 目录"
        fi
    fi
} 

function cleanup() {
    if [ -f "${IMAGE_NAME}" ]; then
        if ask "是否删除已下载的 ${IMAGE_NAME} 文件？" "Y"; then
            log "INFO" "删除下载的镜像文件..."
            rm -f "${IMAGE_NAME}" || log "WARN" "无法删除文件: ${IMAGE_NAME}"
        fi
    fi
} 

function check_dependencies() {
    log "INFO" "检查并安装依赖包..."
    
    if [ ! -f "$PREFIX/etc/apt/sources.list.bak" ]; then
        cp "$PREFIX/etc/apt/sources.list" "$PREFIX/etc/apt/sources.list.bak"
    fi
    
    local mirror_switched=false
    if ! grep -q "mirrors.tuna.tsinghua.edu.cn" "$PREFIX/etc/apt/sources.list"; then
        log "INFO" "更新软件源到清华镜像..."
        sed -i 's@^\(deb.*stable main\)$@#\1\ndeb https://mirrors.tuna.tsinghua.edu.cn/termux/termux-packages-24 stable main@' "$PREFIX/etc/apt/sources.list"
        mirror_switched=true
    fi
    
    log "INFO" "更新包索引..."
    
    # Try update and upgrade with current source (likely Tsinghua)
    if ! apt update -y || ! apt-get -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confnew" dist-upgrade -y; then
        log "WARN" "更新或升级失败 (可能由于镜像源问题)"
        
        # Restore default source if we switched or if we suspect the current source is bad
        log "INFO" "正在恢复默认源并重试..."
        cp "$PREFIX/etc/apt/sources.list.bak" "$PREFIX/etc/apt/sources.list"
        
        log "INFO" "使用默认源重试更新..."
        if ! apt update -y; then
             error_exit "默认源更新失败，请检查您的网络连接。"
        fi
        
        log "INFO" "升级系统 (默认源)..."
        # Try upgrade but don't fail script if it errors on some packages, just warn
        if ! apt-get -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confnew" dist-upgrade -y; then
             log "WARN" "默认源部分包升级失败，尝试继续安装依赖..."
        fi
    fi

    local required_packages=("proot" "tar" "aria2" "wget" "pv")
    local missing_packages=()
    
    for package in "${required_packages[@]}"; do
        if ! command_exists "$package"; then
            missing_packages+=("$package")
        else
            log "INFO" "$package 已就绪"
        fi
    done
    
    if [ ${#missing_packages[@]} -gt 0 ]; then
        log "INFO" "安装缺失的包: ${missing_packages[*]}"
        for package in "${missing_packages[@]}"; do
            log "INFO" "正在安装 $package..."
            if ! apt install -y "$package"; then
                # If install fails, try one last time with default source if we haven't already fully reverted
                # (Logic: If we are still on Tsinghua and it failed here)
                if grep -q "mirrors.tuna.tsinghua.edu.cn" "$PREFIX/etc/apt/sources.list"; then
                    log "WARN" "安装 $package 失败，切换回默认源重试..."
                    cp "$PREFIX/etc/apt/sources.list.bak" "$PREFIX/etc/apt/sources.list"
                    apt update -y
                    if ! apt install -y "$package"; then
                        error_exit "无法安装 $package"
                    fi
                else
                    error_exit "无法安装 $package"
                fi
            fi
        done
    fi
    
    log "INFO" "升级已安装的包..."
    apt upgrade -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" || log "WARN" "包升级过程中出现问题"
}

function get_rootfs() {
    unset KEEP_IMAGE
    if [ -f "${IMAGE_NAME}" ]; then
        if ask "找到现有的 ${IMAGE_NAME} 文件。是否删除并重新下载？" "N"; then
            rm -f "${IMAGE_NAME}" || error_exit "无法删除现有文件"
        else
            log "INFO" "使用现有的 rootfs 归档文件"
            KEEP_IMAGE=1
            return
        fi
    fi
    
    log "INFO" "开始下载 rootfs..."
    ROOTFS_URL="${BASE_URL}/${IMAGE_NAME}"
    if ask "是否使用多线程(aria2c)" "Y"; then
        if command_exists aria2c; then
            log "INFO" "使用 aria2c 多线程下载: ${ROOTFS_URL}"
            aria2c -x 16 -s 16 -k 1M "${ROOTFS_URL}" || error_exit "下载失败"
        else
            log "WARN" "未发现aria2c使用wget下载: ${ROOTFS_URL}"
            wget -c "${ROOTFS_URL}" --progress=bar:force 2>&1 | while read -r line; do
                if [[ "$line" == *%* ]]; then
                    echo -ne "\r${blue}[下载]${reset} $line"
                fi
            done
        fi
    else
        log "INFO" "使用 wget 下载: ${ROOTFS_URL}"
        wget -c "${ROOTFS_URL}" --progress=bar:force 2>&1 | while read -r line; do
            if [[ "$line" == *%* ]]; then
                echo -ne "\r${blue}[下载]${reset} $line"
            fi
        done
    fi
    
    if [ ! -f "${IMAGE_NAME}" ]; then
        rm -f *.tar.xz.*
        error_exit "下载失败: ${IMAGE_NAME} 文件不存在"
    fi
    
    log "SUCCESS" "rootfs 下载完成"
}


# verify_sha function removed as per user request


function extract_rootfs() {
    if [ -z "$KEEP_CHROOT" ]; then
        log "INFO" "开始解压 rootfs..."
        
        pv "$IMAGE_NAME" | proot --link2symlink tar -xJf - \
            --no-same-owner --no-same-permissions --exclude='dev/*' || {
            error_exit "解压失败，请检查镜像文件是否完整"
        }
        
        log "SUCCESS" "rootfs 解压完成"
    else
        log "INFO" "使用现有的 rootfs 目录"
    fi
}

function create_launcher() {
    log "INFO" "创建启动器脚本..."
    
    NH_LAUNCHER=${PREFIX}/bin/nethunter
    NH_SHORTCUT=${PREFIX}/bin/nh
    
    cat > "$NH_LAUNCHER" <<- EOF
#!/data/data/com.termux/files/usr/bin/bash -e
cd \${HOME}
unset LD_PRELOAD
if [ ! -f $CHROOT/root/.version ]; then
    touch $CHROOT/root/.version
fi
user="$USERNAME"
home="/home/\$user"
start="sudo -u kali /bin/bash"
if grep -q "kali" ${CHROOT}/etc/passwd; then
    KALIUSR="1";
else
    KALIUSR="0";
fi
if [[ \$KALIUSR == "0" || ("\$#" != "0" && ("\$1" == "-r" || "\$1" == "-R")) ]];then
    user="root"
    home="/\$user"
    start="/bin/bash --login"
    if [[ "\$#" != "0" && ("\$1" == "-r" || "\$1" == "-R") ]];then
        shift
    fi
fi
cmdline="proot \\
        --link2symlink \\
        -0 \\
        -r $CHROOT \\
        -b /dev \\
        -b /proc \\
        -b /sdcard \\
        -b $CHROOT\$home:/dev/shm \\
        -w \$home \\
           /usr/bin/env -i \\
           HOME=\$home \\
           PATH=/usr/local/sbin:/usr/local/bin:/bin:/usr/bin:/sbin:/usr/sbin \\
           TERM=\$TERM \\
           LANG=zh_CN.UTF-8 \\
           \$start"
cmd="\$@"
if [ "\$#" == "0" ];then
    exec \$cmdline
else
    \$cmdline -c "\$cmd"
fi
EOF
    chmod 700 "$NH_LAUNCHER"
    
    if [ -L "${NH_SHORTCUT}" ]; then
        rm -f "${NH_SHORTCUT}"
    fi
    if [ ! -f "${NH_SHORTCUT}" ]; then
        ln -s "${NH_LAUNCHER}" "${NH_SHORTCUT}" >/dev/null
    fi
    
    log "SUCCESS" "启动器创建完成"
}

function create_kex_launcher() {
    log "INFO" "创建 KeX 启动器..."
    KEX_LAUNCHER=${CHROOT}/usr/bin/kex
    cat > $KEX_LAUNCHER <<- EOF
#!/bin/bash
function start-kex() {
    export FONTCONFIG_PATH=/etc/fonts
    export FONTCONFIG_FILE=/etc/fonts/fonts.conf
    if [ ! -f ~/.vnc/passwd ]; then
        passwd-kex
    fi
    USR=\$(whoami)
    if [ \$USR == "root" ]; then
        SCREEN=":2"
    else
        SCREEN=":1"
    fi 
    export MOZ_FAKE_NO_SANDBOX=1; export HOME=\${HOME}; export USER=\${USR}; LD_PRELOAD=/usr/lib/aarch64-linux-gnu/libgcc_s.so.1 nohup vncserver \$SCREEN >/dev/null 2>&1 </dev/null
    starting_kex=1
    return 0
}
function stop-kex() {
    rm -f /tmp/.X*-lock
    rm -f /tmp/.X11-unix/X*
    vncserver -kill :1 >/dev/null 2>&1
    vncserver -kill :2 >/dev/null 2>&1
}
function passwd-kex() {
    vncpasswd
    return $?
}
function status-kex() {
    sessions=\$(vncserver -list | sed s/"TigerVNC"/"NetHunter KeX"/)
    if [[ \$sessions == *"590"* ]]; then
        printf "\n\${sessions}\n"
        printf "\n你可以使用 KeX 客户端连接这些显示器。\n\n"
    else
        if [ ! -z \$starting_kex ]; then
            printf '\n启动 KeX 服务器时出错。\n请尝试 "nethunter kex kill" 或重启 termux 会话并重试。\n\n'
        fi
    fi
    return 0
}
function kill-kex() {
    pkill Xtigervnc
    return \$?
}
case \$1 in
    start)
        start-kex
        ;;
    stop)
        stop-kex
        ;;
    status)
        status-kex
        ;;
    passwd)
        passwd-kex
        ;;
    kill)
        kill-kex
        ;;
    *)
        stop-kex
        sleep 1
        start-kex
        status-kex
        ;;
esac
EOF
    chmod 700 $KEX_LAUNCHER
    log "SUCCESS" "KeX 启动器创建完成"
}

function fix_profile_bash() {
    log "INFO" "修复 bash profile..."
    if [ -f ${CHROOT}/root/.bash_profile ]; then
        sed -i '/if/,/fi/d' "${CHROOT}/root/.bash_profile"
    fi
}

function fix_resolv_conf() {
    log "INFO" "配置 DNS 解析..."
    
    resolv_conf="$CHROOT/etc/resolv.conf"
    cat > "$resolv_conf" <<EOF
nameserver 9.9.9.9
nameserver 149.112.112.112
nameserver 8.8.8.8
nameserver 114.114.114.114
EOF
    log "SUCCESS" "DNS 配置已完成"
}

function fix_sudo() {
    log "INFO" "配置 sudo 权限..."
    
    chmod +s "$CHROOT/usr/bin/sudo" || log "WARN" "无法设置 sudo 权限"
    chmod +s "$CHROOT/usr/bin/su" || log "WARN" "无法设置 su 权限"
    echo "kali    ALL=(ALL:ALL) ALL" > $CHROOT/etc/sudoers.d/kali
    
    echo "Set disable_coredump false" > $CHROOT/etc/sudo.conf
    
    log "SUCCESS" "sudo 权限配置完成"
}

function fix_uid() {
    log "INFO" "修复用户 ID 映射..."
    USRID=$(id -u)
    GRPID=$(id -g)
    nh -r usermod -u "$USRID" kali 2>/dev/null || log "WARN" "无法修改用户 ID"
    nh -r groupmod -g "$GRPID" kali 2>/dev/null || log "WARN" "无法修改用户 ID" 
    log "SUCCESS" "用户 ID 映射完成"
}

function update_sources_list() {
    log "INFO" "更新 Kali 软件源..."
    
    sources_list="$CHROOT/etc/apt/sources.list"
    if [ -f "$sources_list" ]; then
        cp "$sources_list" "$sources_list.backup"
        
        sed -i "s@http://http.kali.org/kali@https://mirrors.tuna.tsinghua.edu.cn/kali@g" "$sources_list"
        log "SUCCESS" "Kali 软件源已更新为清华镜像"
        if [ "$wimg" = "nano" ] || [ "$wimg" = "minimal" ]; then
            log "INFO" "当前非完整版,正在安装kex服务"
            nh -r apt update && nh -r apt install -y tightvncserver kali-desktop-xfce tigervnc-standalone-server x11-utils x11-xserver-utils xfonts-base
        fi
    else
        log "WARN" "未找到 Kali sources.list 文件"
    fi
}

function print_banner() {
    clear
    printf "${blue}##################################################${reset}\n"
    printf "${blue}##                                              ##${reset}\n"
    printf "${blue}##  88      a8P         db        88        88  ##${reset}\n"
    printf "${blue}##  88    .88'         d88b       88        88  ##${reset}\n"
    printf "${blue}##  88   88'          d8''8b      88        88  ##${reset}\n"
    printf "${blue}##  88 d88           d8'  '8b     88        88  ##${reset}\n"
    printf "${blue}##  8888'88.        d8YaaaaY8b    88        88  ##${reset}\n"
    printf "${blue}##  88P   Y8b      d8''''''''8b   88        88  ##${reset}\n"
    printf "${blue}##  88     '88.   d8'        '8b  88        88  ##${reset}\n"
    printf "${blue}##  88       Y8b d8'          '8b 888888888 88  ##${reset}\n"
    printf "${blue}##                                              ##${reset}\n"
    printf "${blue}####  ############ (Kali Nethunter) ####################${reset}\n"
    printf "${blue}X黑手汉化多线程下载脚本中文版此脚本已将kali汉化不必担心看不懂${reset}\n"
    printf "使用则代表同意协议:\n"
    printf "作者已经脚本参与者不承担因脚本引发的任何法律问题,均由使用者自行承担\n\n"
}

function main() {
    red='\033[1;31m'
    green='\033[1;32m'
    yellow='\033[1;33m'
    blue='\033[1;34m'
    light_cyan='\033[1;96m'
    reset='\033[0m'
    blue_bg='\033[44;1m'
    yellow_bg='\033[43;1m'
    red_bg='\033[41;1m'
    blue_fg_strong='\033[34;1m'
    yellow_fg_strong='\033[33;1m'
    red_fg_strong='\033[31;1m'
    
    cd "$HOME"
    
    print_banner
    get_arch
    set_strings
    prepare_fs
    check_dependencies
    get_rootfs
    # verify_sha (User requested to skip verification)
    extract_rootfs
    create_launcher
    cleanup
    
    log "INFO" "为 Termux-X 配置 NetHunter..."
    fix_profile_bash
    fix_resolv_conf
    fix_sudo
    create_kex_launcher
    fix_uid
    update_sources_list
    sleep 3
    print_banner
    log "SUCCESS" "Kali NetHunter 已成功安装到 Termux-X"
    
    echo
    printf "${green}=== 使用说明 ===${reset}\n"
    printf "${green}[+] nethunter             # 启动 NetHunter${reset}\n"
    printf "${green}[+] nh                    # 快速启动 NetHunter${reset}\n"
    printf "${green}[+] exit                  # 在 NetHunter 中退出${reset}\n\n"
    
    printf "${green}=== KeX 图形界面 ===${reset}\n"
    printf "${green}[+] nethunter kex passwd  # 设置 KeX 密码${reset}\n"
    printf "${green}[+] nethunter kex &       # 启动图形界面${reset}\n"
    printf "${green}[+] nethunter kex stop    # 停止图形界面${reset}\n"
    printf "${green}[+] nethunter kex status  # 查看状态${reset}\n\n"
    
    printf "${green}=== 高级选项 ===${reset}\n"
    printf "${green}[+] nethunter -r          # 以 root 身份启动${reset}\n"
    printf "${green}[+] nethunter kex kill    # 强制停止所有 KeX 会话${reset}\n"
    echo
}

main "$@"
