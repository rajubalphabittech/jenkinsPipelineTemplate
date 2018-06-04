// Pipeline

pipeline {

    agent any

    environment {
        DOCKER_IMAGE_BUILD_NAME = 'ciserver'
        DOCKER_REGISTRY = 'registry.zombox.it'
        DOCKER_AWS_REGISTRY = 'xxxxxxxxx.dkr.ecr.eu-west-1.amazonaws.com'
        GIT_REPOSITORY = 'https://github.com/fabriziogaliano/'
        DEPLOY_SSH_TARGET = '127.0.0.1'
        DEPLOY_SSH_USER = 'root'
        DEPLOY_SSH_DEFAULT_PATH = '/docker'
    }

    // Git checkout

    stages {
        stage('Git Checkout') {
            agent any
            steps {
                echo "--------------------------------------------------------------"
                echo "----------------------> Project Update <----------------------"
                echo "--------------------------------------------------------------"
                checkout([$class: 'GitSCM', branches: [[name: '*/${GIT_REF}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'aad8cb5b-ddd8-47e3-a8d4-b9f128cf3fd5', url: 'https://github.com/fabriziogaliano/${JOB_NAME}.git']]])
                echo "----------------------> Project Updated <---------------------"
            }
        }

        // Build 

        stage('Docker Build') {
            steps {
                echo "-------------------------------------------------------------"
                echo "----------------------> Project Build <----------------------"
                echo "-------------------------------------------------------------"
                dockerBuild()
                echo "----------------------> Project Builded <--------------------"
            }
        }

        // Tag/Push docker images, conditional steps to check if Dev/prod environment

        stage('Docker Tag/Push') {
            steps {
                script {
                    if (env.GIT_REF == 'develop') {
                        echo "---------------------------------------------------------------"
                        echo "----------------------> Docker Tag/Push <----------------------"
                        echo "---------------------------------------------------------------"
                        dockerTag()
                        dockerPush()
                        echo "----------------------> Docker Tag/Push OK <-------------------"
                        cleanUp()
                        echo "---------------------------------------------------------------------------------"
                        echo "----------------------> Old images removed from CI Server <----------------------"
                        echo "---------------------------------------------------------------------------------"
                    } else {
                        echo "-------------------------------------------------------------------"
                        echo "----------------------> Docker AWS Tag/Push <----------------------"
                        echo "-------------------------------------------------------------------"
                        dockerAwsTag()
                        dockerAwsPush()
                        echo "----------------------> Docker AWS Tag/Push OK <-------------------"
                        // cleanAwsUp()
                        echo "---------------------------------------------------------------------------------"
                        echo "----------------------> Old images removed from CI Server <----------------------"
                        echo "---------------------------------------------------------------------------------"
                    }
                }
            }
        }

        // Project Deploy, conditional steps to check if Dev/prod environment

        stage('Deploy') {
            steps {
                    deployInf()
                }
            }

    }
}

// Main functions

def deployInf() {
    if ( env.DEPLOY_ENV == 'dev' ) {

        DEPLOY_SSH_TARGET = '192.168.0.109'
        DEPLOY_ENV = 'DEVELOPMENT!'
      
        echo "---------------> Deploy Infrastructure ------> ${DEPLOY_ENV}"

        echo "-------------------------------------------------------"
        echo "----------------------> Deploy! <----------------------"
        echo "-------------------------------------------------------"
        deploy()
        echo "------> Deploy OK to ${DEPLOY_ENV} Environment <-------"
    } 
    
    else {

        DEPLOY_SSH_TARGET = '192.168.0.109'
        DEPLOY_ENV = 'PRODUCTION!'

        echo "---------------> Deploy Infrastructure ------> ${DEPLOY_ENV}"

        echo "-------------------------------------------------------"
        echo "----------------------> Deploy! <----------------------"
        echo "-------------------------------------------------------"
        deploy()
        echo "------> Deploy OK to ${DEPLOY_ENV} Environment <-------"
    }
}

def dockerBuild() {
    node {
        sh 'docker build -t ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} .'
    }
}

def dockerTag() {
    node {
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${JOB_NAME}:latest'
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF}'
    }
}

def dockerAwsTag() {
    node {
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest'
        sh 'docker tag ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF}'
    }
}

def dockerPush() {
    node {
        withDockerRegistry(credentialsId: '655afa6d-5a19-4f15-97ce-29ac43336234', url: 'https://${DOCKER_REGISTRY}') {
        sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF}'
        sh 'docker push ${DOCKER_REGISTRY}/${JOB_NAME}:latest'
    }
    }
}

def dockerAwsPush() {
    node {
        withDockerRegistry(credentialsId: '655afa6d-5a19-4f15-97ce-29ac43336234', url: 'https://${DOCKER_AWS_REGISTRY}') {
        sh 'docker push ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF}'
        sh 'docker push ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest'
    }
    }
}

def cleanUp() {
    node {
        sh 'docker rmi ${DOCKER_REGISTRY}/${JOB_NAME}:${GIT_REF} '
        sh 'docker rmi ${DOCKER_REGISTRY}/${JOB_NAME}:latest '
        sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} '
    }
}

def cleanAwsUp() {
    node {
        sh 'docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:${GIT_REF} '
        sh 'docker rmi ${DOCKER_AWS_REGISTRY}/${JOB_NAME}:latest'
        sh 'docker rmi ${DOCKER_IMAGE_BUILD_NAME}/${JOB_NAME}:${GIT_REF} '
    }
}

def deploy() {
    if (env.DEPLOY_MODE == "docker-compose") {
        node {
            sh 'docker-compose -f ${DEPLOY_SSH_DEFAULT_PATH}/${JOB_NAME}_ci/docker-compose.yml up -d --force-recreate'
            echo "Deployed with DOCKER-COMPOSE"
        } 
    } else {
        node {
            sh 'docker-compose -f ${DEPLOY_SSH_DEFAULT_PATH}/${JOB_NAME}_ci/docker-compose.yml up -d --force-recreate'
            echo "Deployed with SWARM mode"
        }
    }
}
