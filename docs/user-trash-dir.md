Getting the path to a user's trash directory
--------------------------------------------
__URL Path__: /user-trash-dir

__HTTP Method__: GET

__Action__: user-trash-dir

__Error codes__: ERR_NOT_A_USER

__Request Parameters__:
* user - The iRODS username of the user making the request.

__Response Body__:

    {
        "action" : "user-trash-dir",
        "status" : "success",
        "trash" : "/iplant/trash/home/proxy-user/johnworth"
    }

__Curl Command__:

    curl http://sample.nibblonian.org/user-trash-dir?user=johnworth