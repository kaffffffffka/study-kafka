# Zookeeper
Zookeeper는 분산 코디네이션 서비스를 제공한다. Kafka의 클러스터 설정 리더 정보, 컨트롤러 정보를 담고 있어 Kafka를 실행하는 데에 필요한 필수 application이다.
<br/>
<br/>
*분산 코디네이션 서비스: 분산 시스템에서 시스템 간의 정보 공유, 상태 체크, 서버들 간의 동기화를 위한 락 등을 처리해주는 서비스

<img width="748" alt="스크린샷 2024-11-12 오후 11 11 54" src="https://github.com/user-attachments/assets/371229cc-91ba-4c7b-900f-a39e08869ae4">
<br/>
<br/>

Zookeeper는 분산 시스템의 일부이기 때문에 동작을 멈춘다면, 전체 서비스에 영향을 줄 수 있다.
따라서 안전성을 위해 클러스터로 운영된다. <br/>
Zookeeper는 특정 서버에 문제가 생겼을 경우, 과반수 이상의 데이터를 기준으로 일관성을 맞추기 때문에 클러스터는 홀수로 구성하는 게 일반적이다. 
<br/>
<br/>
이때 클러스터를 앙상블(ensemble)이라고 한다.

<br/>
<br/>

## Leader-Follower
ZooKeeper의 리더-팔로워 구조는 분산 시스템에서 안정성과 가용성을 높이기 위해 사용된다. <br/>
이 구조는 ZooKeeper 서버들 간의 역할을 나누어 장애 상황에서도 서비스가 지속적으로 동작할 수 있도록 합니다. <br/>

***1. Leader(리더)***: 리더는 클러스터 내에서 주요 결정과 쓰기 요청을 처리하는 역할을 담당한다. <br/>
클라이언트가 ZooKeeper에 쓰기 요청을 보낼 때, 리더는 이 요청을 받아들이고,다른 서버(팔로워)들에게 변경 사항을 전파하고, <br/>
투표 과정을 통해 변경 사항을 합의한다. 합의가 이루어지면 클러스터의 데이터가 동기화된다. <br/>
<br/>
***2.	Follower(팔로워)***: 팔로워는 클라이언트로부터 읽기 요청을 처리하거나 리더의 쓰기 요청을 기다리는 역할을 수행한다. <br/>
팔로워는 리더의 지시를 받아 데이터의 일관성을 유지하고, 클라이언트의 쓰기 요청이 있을 경우 리더에게 전달한다. <br/>
팔로워는 클러스터 내에서 리더 선출에도 참여하며, 새로운 리더가 필요할 때 리더 선출 과정을 통해 리더가 선택된다. <br/>
<br/>
<br/>
Leader가 새로운 트랜잭션을 수행하기 위해서는 자신을 포함하여 과반수 이상의 서버로부터 합의를 얻어야 한다. 
과반수의 합의를 위해 필요한 서버들을 Quorum이라고 한다. Ensemble을 구성하는 서버의 수가 5개라면, Quorum은 3개의 서버로 구성이 된다.