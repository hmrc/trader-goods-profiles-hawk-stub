
# trader-goods-profiles-hawk-stub

This is service is a stateful stub of the B&T HAWK system to be used for testing.

There is a bruno collection that can be used for manual testing in the [bruno](bruno) directory

## Endpoints

All endpoints follow the relevant EIS schemas

### Trader Profiles

#### Maintain Trader Profile
`PUT /tgp/maintainprofile/v1`

[Bruno request](bruno/trader-profile/Maintain%20Trader%20Profile.bru)

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

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").