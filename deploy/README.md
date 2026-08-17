# EC2 배포

## 서버 구성 (이미 적용됨)

- 앱 경로: `/opt/afterglow/afterglow-be.jar`
- 환경변수: `/opt/afterglow/afterglow.env` (**Git에 올리지 않음**)
- systemd: `afterglow.service`
- DB: RDS PostgreSQL (`afterglow-db`, ap-northeast-2, db.t4g.micro, 20GB gp3, 단일 AZ).
  같은 VPC(`vpc-07890615839daff50`) 내부 전용이며 퍼블릭 접근은 막혀 있고,
  `afterglow-be-sg`(앱 EC2)에서만 5432 인바운드 허용. 스키마는 `ddl-auto=update`로 앱이 자동 생성.

## GitHub Actions Secrets

Repository → Settings → Secrets and variables → Actions 에 등록:

| Secret | 설명 |
|--------|------|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 DNS |
| `EC2_USER` | 보통 `ec2-user` |
| `EC2_SSH_KEY` | `afterglow-be-key.pem` **전체 내용** |

`main` 브랜치에 push 하면 JAR 빌드 → EC2 업로드 → `systemctl restart afterglow` 가 실행됩니다.

## 로컬에서 수동 배포

```powershell
.\gradlew.bat bootJar
scp -i $env:USERPROFILE\.ssh\afterglow-be-key.pem build\libs\afterglow-be-0.0.1-SNAPSHOT.jar ec2-user@HOST:/opt/afterglow/afterglow-be.jar
ssh -i $env:USERPROFILE\.ssh\afterglow-be-key.pem ec2-user@HOST "sudo systemctl restart afterglow"
```
