# MusicBot
MusicBot for Discord

다른 뮤직봇들의 경우에는 봇에게 과도한 명령을 줄 경우 Rate Limit에 걸리게 됨.  
이 방법을 해결하기 위해 'X-RateLimit-Reset' Response Header를 이용하여 Rate Limit에 걸리지 않게 설계를 함.  
봇이 명령에 대한 응답을 하고(메세지 삭제) 채널에 대한 정보를 받아오고, 재생중인 메세지를 업데이트 하기까지 총 3번의 REST API 전송이 필요함.

메세지에 대한 응답  
POST /channels/{channel.id}/messages/bulk-delete OR DELETE /channels/{channel.id}/messages/{message.id}  

메세지 업데이트
GET /channels/{channel.id}  
PUT /channels/{channel.id}/permissions/{overwrite.id}

위와 같은 REST API를 전송할 경우 약 초당 5개의 응답만을 전송할 수가 있음.  
만약 실시간으로 응답할 경우 여러 응답이 들어오면 429 Too Many Requests을 받아 봇이 응답을 할 수가 없게됨.  
이를 실시간으로 응답을 하는 것이 아닌 들어온 메세지를 받아 RateLimit가 리셋 될 때 마다 한 번씩 응답을 하게되면 제한에 걸리지 않고 응답이 가능해짐.

English:  
  
In the case of other music bots, if excessive commands are given to the bot, it will be subject to rate limit.
In order to solve this method, 'X-RateLimit-Reset' Response Header is used to design so as not to be hit by Rate Limit.
A total of 3 REST API transmissions are required until the bot responds to the command (deleting the message), receives information about the channel, and updates the message being played.

reply to message
POST /channels/{channel.id}/messages/bulk-delete OR DELETE /channels/{channel.id}/messages/{message.id}

message update
GET /channels/{channel.id}
PUT /channels/{channel.id}/permissions/{overwrite.id}

When sending the REST API as above, only about 5 responses can be sent per second.
If responding in real time, if multiple responses come in, 429 Too Many Requests will be received and the bot will not be able to respond.
Instead of responding in real time, if you receive an incoming message and respond once every time RateLimit is reset, you can respond without being hit by the limit.