
# trader-goods-profiles-hawk-stub

This is service is a stateful stub of the B&T HAWK system to be used for testing.

There is a bruno collection that can be used for manual testing in the [bruno](bruno) directory

## Endpoints

All endpoints follow the relevant EIS schemas

### Trader Profiles

#### Maintain Trader Profile
`PUT /tgp/maintainprofile/v1`

[Bruno request](bruno/trader-profile/Maintain%20Trader%20Profile.bru)

#### Get Profile
`GET /tgp/getprofile/v1`

[Bruno request](bruno/trader-profile/Get%20Profile.bru)

### Goods Item Records

#### Create Goods Item Record
`POST /tgp/createrecord/v1`

[Bruno request](bruno/goods-item-record/Create%20Goods%20Item%20Record.bru)

#### Get Goods Item Records
`GET /tgp/getrecords/v1/:eori`

[Bruno request](bruno/goods-item-record/Get%20Goods%20Item%20Records.bru)

`GET /tgp/getrecords/v1/:eori/:recordId`

[Bruno request](bruno/goods-item-record/Get%20Goods%20Item%20Record.bru)

#### Update Goods Item Record
`PUT /tgp/updaterecord/v1`

[Bruno request](bruno/goods-item-record/Update%20Goods%20Item%20Record.bru)

#### Remove Goods Item Record
`PUT /tgp/removerecord/v1`

[Bruno request](bruno/goods-item-record/Remove%20Goods%20Item%20Record.bru)

### Test Support APIs

#### Patch Goods Item Record
`PATCH /test-support/goods-item`

### Precheck

it will run all the tests, as well as check the format and coverage report.
```bash
./precheck.sh
```


### Run Unit Tests

A running instance of MongoDb is needed to run the Unit Test.
Once you have MongoDb up and running, type the following command to
run the unit tests.

```
sbt test
```

### Running the Integration test

A running instance of MongoDb with replicaset is needed to run
the integration test. This is because one of the Repository test uses
transactions.

More information about transaction can be found on the [Transactional mongo for development](https://confluence.tools.tax.service.gov.uk/display/TEC/2021/09/20/Transactional+mongo+for+development)
confluence page

Once you have an instance of MongoDb with replica set running type the
following command to run the test:

```
sbt it/test
```

### Run MongoDb with replicaset with docker

To run an instance of MongoDb with replica set see [Transactional mongo for development](https://confluence.tools.tax.service.gov.uk/display/TEC/2021/09/20/Transactional+mongo+for+development)
confluence page or do the following:

1. Open a window terminal
2. type the following docker command and press enter

   ```
   docker run -p 27017:27017 --name mongo1 percona/percona-server-mongodb:4.4 mongod --replSet rs0
    ``` 
   
   This will create and start a container called mongo1. Make sure you have the image
   percona/percona-server-mongodb:4.4 downloaded or you can download a different one

3. then type and enter the following command
    ```
    docker exec -it mongo1 mongo
    ```
   this will allow to run command inside a container

4. Then from the container type and run the following command

   ```
   rs.initiate()
   ```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").