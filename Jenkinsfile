pipeline {
    agent any

    stages {
        stage('1. Clean & APK 빌드') {
            steps {
                bat 'call gradlew.bat clean assembleRelease'
            }
        }

        stage('2. 파일 복사 및 백업') {
            steps {
                bat '''
                    @echo off
                    set OUTPUT_DIR=C:\\Android_Build_Outputs
                    if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
                    
                    copy "app\\build\\outputs\\apk\\release\\app-release.apk" "%OUTPUT_DIR%\\app-release_b%BUILD_NUMBER%.apk"
                '''
            }
        }
    }

    post {
        success {
            echo "빌드 성공! 웹사이트 대시보드에 APK를 보관합니다."
            archiveArtifacts artifacts: 'C:/Android_Build_Outputs/app-release_b*.apk', fingerprint: true
        }
    }
}
