<<<<<<< HEAD
pipeline {
    agent any

    stages {
        stage('1. Clean & APK 빌드') {
            steps {
                // 윈도우 환경이므로 bat 명령어를 사용합니다.
                bat 'call gradlew.bat clean assembleRelease'
            }
        }

        stage('2. 파일 복사 및 백업') {
            steps {
                bat '''
                    @echo off
                    set OUTPUT_DIR=C:\\Android_Build_Outputs
                    if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
                    
                    :: Jenkins가 제공하는 %BUILD_NUMBER% 변수를 활용하여 파일명 지정
                    copy "app\\build\\outputs\\apk\\release\\app-release.apk" "%OUTPUT_DIR%\\app-release_b%BUILD_NUMBER%.apk"
                '''
            }
        }
    }

    post {
        success {
            echo "빌드 성공! 웹사이트 대시보드에 APK를 보관합니다."
            // Jenkins 웹 화면에 빌드 번호가 붙은 APK 파일을 아티팩트로 노출합니다.
            archiveArtifacts artifacts: 'C:/Android_Build_Outputs/app-release_b*.apk', fingerprint: true
        }
        failure {
            echo "빌드에 실패했습니다. 에러 로그를 확인하세요."
        }
    }
}
=======
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
>>>>>>> 1be04a9caafe30516d518108ddeff939d99c80b6
