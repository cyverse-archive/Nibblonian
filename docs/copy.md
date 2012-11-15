Copying a file or directory
---------------------------
The "from" field is a list of paths to a file or folder. Files and folders can be mixed in together in the "from" field. The "to" field needs to be the path to the destination folder.

Action: "copy"

Error codes: ERR_EXISTS, ERR_DOES_NOT_EXIST, ERR_NOT_A_USER, ERR_NOT_WRITEABLE

Sample Request JSON:

    {
        "paths" : ["<absolute path to file/directory>"],
        "destination" : "<absolute path to a directory"
    }

Curl Command:

    curl -H "Content-Type:application/json" -d '{"paths" : "/iplant/home/wregglej/foo1.fq", "destination" : "/iplant/home/wregglej/blah"}' http://sample.nibblonian.org/copy?user=wregglej

Response JSON:

    {
        "action" : "copy",
        "status" : "success",
        "sources" : ["\/iplant\/home\/wregglej\/foo1.fq"],
        "dest" : "\/iplant\/home\/wregglej\/blah"
    }

Getting the path to a user's trash directory
--------------------------------------------
Action: "user-trash-dir"

Error codes: ERR_NOT_A_USER

Curl Command:

    curl http://sample.nibblonian.org/user-trash-dir?user=johnworth

Response JSON:

    {
        "action" : "user-trash-dir",
        "status" : "success",
        "trash" : "/iplant/trash/home/proxy-user/johnworth"
    }

