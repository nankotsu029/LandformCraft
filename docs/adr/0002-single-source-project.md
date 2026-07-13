# 0002: 初期実装を単一srcへ集約する

- Status: Accepted
- Date: 2026-07-13

## Context

初期構成では、AI、model、generator、format、Paper、CLIの依存方向をbuild時に強制するため、12個のGradle subprojectへ分割しました。しかしPhase 0の実装量に対してdirectory、build file、artifactが多く、コードを探す負担とGradle設定の複雑さが大きくなりました。

## Decision

- production codeをルートの`src/main/java`へ集約する
- resourceを`src/main/resources`、testを`src/test/java`へ集約する
- 責務境界は`model`、`core`、`ai`、`generator`、`format`、`paper`等のJava packageで維持する
- Gradleは単一projectとする
- subproject追加は独立配布、別runtime、依存隔離など具体的な必要性が生じた場合だけ行う

## Consequences

- 初期開発でコード、test、resourceを追いやすくなる
- Gradle設定とartifact構成が単純になる
- package間の依存規則はbuildだけでは完全に強制できないため、reviewと将来のarchitecture testが必要になる
- WorkerやWeb UIを独立processとしてreleaseする段階では再分割する可能性がある

## Alternatives

### 12個のsubprojectを維持する

依存を強制できますが、現時点では空に近いprojectが多く、開発負担が利益を上回るため不採用です。

### すべてを1つのpackageへ置く

依存方向と責務が不明瞭になるため不採用です。filesystemは集約してもpackage境界は維持します。
