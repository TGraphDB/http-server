#!/bin/bash
cd /db/bin/tgraphdb-http-server


# default
function start_db_server() {
    cd /db/bin/tgraphdb-http-server
    mvn -B --offline compile exec:java -Dexec.mainClass=app.Application
}

# 处理命令行参数
main() {
    # 如果没有参数，执行默认函数
    if [ $# -eq 0 ]; then
        start_db_server
    else
        # 遍历所有参数并执行对应的函数
        for arg in "$@"; do
            if [ "$(type -t "$arg")" = "function" ]; then
                $arg  # 调用对应函数
            else
                echo "Warning: Bash function '$arg' undefined, use args as cmd."
                $arg
#                exit 1
            fi
        done
    fi
}

# 调用主函数并传递所有参数
main "$@"
