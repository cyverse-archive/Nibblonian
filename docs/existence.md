File/Directory existence
------------------------
The /exists endpoint allows the caller to check for the existence of a set of files. The following is an example call to the exists endpoint:

Action(s): exists

Error codes: ERR_NOT_A_USER

Curl command:

    curl -H "Content-type:application/json" -d '{"paths" : ["/iplant/home/wregglej/pom.xml", "/iplant/home/wregglej/pom.xml2"]}' 'http://127.0.0.1:3000/exists?user=wregglej'

Response:

    {
        "action":"exists",
        "status":"success",
        "paths":{
            "/iplant/home/wregglej/pom.xml2":false,
            "/iplant/home/wregglej/pom.xml":false
        }
    }

