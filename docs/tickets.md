Creating Tickets
----------------
__URL Path__: /tickets

__HTTP Method__: POST

__Action__: "add-tickets"

__Error Codes__: ERR_NOT_A_USER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

__Request Parameters__:
* user - The iRODS username of the user making the request.

* public - Tells Nibblonian whether to make the ticket accessible to the public group. 
           Setting it to 1 means that the ticket will be made publicly accessible. Any other value 
           means that the ticket will not be accessible publicly. This parameter is optional and 
           defaults to not making tickets public.

__Request Body__:

    {
        "tickets" : [
            {
                "path" : "/path/to/file/or/directory",
                "ticket-id" : "String representation of a ticket."
            }
        ]
    }

The "ticket-id" field is the user supplied string that is associated with the ticket. This is a 
required field.

__Response Body__:

    {
        "action" : "add-tickets",
        "user" : "<username>",
        "tickets" : [
            {
                "path" : "/path/to/file/or/directory",
                "ticket-id" : "<ticket-id>",
                "expiry" : "<expiration date>",
                "uses-limit" : "<uses-limit",
            }
        ]
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"tickets":[{"path" : "/path/to/file/or/directory","ticket-id" : "String representation of a ticket."}]}' 'http://127.0.0.1:3000/tickets?user=<username>&public=1'


Listing Tickets
---------------
__URL Path__: /list-tickets

__HTTP Method__: POST

__Action__: "list-tickets"

__Error Codes__: ERR_NOT_A_USER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

__Request Parameters__:
* user - The iRODS username associated with the request.

__Request Body__:

    {
        "paths" : ["list of paths to look up tickets for"]
    }

__Response Body__:

    {
        "action" : "list-tickets",
        "tickets" : {
            "/path/to/file" : [
                {
                    "path" : "/path/to/file",
                    "ticket-id" : "<ticket-id>",
                    "expiry" : "<expiration date>",
                    "uses-limit" : "<uses-limit",
                }
            ],
            "/path/to/dir"  : [
                {
                    "path" : "/path/to/dir",
                    "ticket-id" : "<ticket-id>",
                    "expiry" : "<expiration date>",
                    "uses-limit" : "<uses-limit",
                }
            ]
        }
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"paths":{"/path/to/file":["ticket-id1","ticket-id2","ticket-id3"],"/path/to/dir":["ticket-id4","ticket-id5"]}}' http://127.0.0.1:3000/list-tickets?user=<username>


Deleting Tickets
----------------
__URL Path__: /delete-tickets

__HTTP Method__: POST

__Action__: "delete-tickets"

__Error Codes__: ERR_NOT_A_USER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

__Request Parameters__:
* user - The iRODS username associated with the request.

__Request Body__:

    {
        "tickets" : ["ticket-id1", "ticket-id2"]
    }

__Response Body__:

    {
        "action" : "delete-tickets",
        "tickets" : ["ticket-id1", "ticket-id2"]
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"tickets":["ticket-id1","ticket-id2"]}' http://127.0.0.1:4000/delete-tickets?user=<username>