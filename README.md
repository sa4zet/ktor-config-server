![Build and push docker image](https://github.com/sa4zet/ktor-config-server/workflows/Build%20and%20push%20docker%20image/badge.svg)

# Lightweight configuration server using HOCON files

## Pull latest image

`docker pull sa4zet/ktor-config-server`

## The config_server_basic_auth_salt environment variable

You have to set that environment variable when you generating user password hash-es or running the application.
It is safer way than store your salt in you configuration file.

## Generate user password hashes

```
docker run \
-it \
--env="config_server_basic_auth_salt=## YOUR SALT ##" \
sa4zet/ktor-config-server digest
```

You can terminate the hash generating process with `CTRL+D`.

```
Enter a not empty password: my password
70qvIY4/MAM5n1zB+FqavY0QfzZ2vLBTYaGa3Sa+M5fIZyvkinknVtDtEmpVIZkd5rV2CkAUF5FRiJHfvXVdtg==
Enter a not empty password: another password
6FHh+AZMH/U10kxa1QUTfbTcWkUiFtv8gD2NOlyX941GsY/QNvmyb0YZT1wQUAXFSFABOGnePkQsNWONgX8QAg==
Enter a not empty password:
Bye!
```

## Configuration
```
git {
  //  remote {url = "ssh://remote-server/remote-git-path"}
  //  remote {url = "git@github.com/lightbend/config"}
  remote {url = "file:///tmp/config-example/remote/"} # config remote git repository
  local {dir = "/tmp/configurations"} # config local clone directory
}

auth {
  basic {
    users = [
      {
        userName = "read only user name", # basic auth username
        userHash = "GENERATED HASH", # generated basic auth password hash
        permission = "r" # read-only permission
      },
      {
        userName = "read/write user name", # basic auth username
        userHash = "GENERATED HASH", # generated basic auth password hash
        permission = "rw" # read/write permission
      }
    ]
  }
}
```

## Run

```
docker run \
--env="config_server_basic_auth_salt=## YOUR SALT ##" \
--mount="type=bind,readonly,source=## YOUR SSH CONFIG ON HOST ##,destination=/home/config-server/.ssh/" \
--mount="type=bind,readonly,source=## YOUR CONFIG ON HOST ##,destination=/app.conf" \
--publish="127.0.0.1:80:5454/tcp" \
sa4zet/ktor-config-server -config=/app.conf
```

## Config remote git repository
You can read configuration file with GET method.

`curl -X GET "http://user:pwd@localhost:5454/cfg/EXAMPLE"`

You can read configuration subtree with GET method.

`curl -X GET "http://user:pwd@localhost:5454/cfg/EXAMPLE/a/b"`

# Source

https://github.com/sa4zet/ktor-config-server

# License
```
BSD 3-Clause License

Copyright (c) 2020, Zsolt Salamon
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of Zsolt Salamon nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZSOLT SALAMON AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZSOLT SALAMON OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```