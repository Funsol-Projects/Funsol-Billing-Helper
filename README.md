# Funsol Billing Helper


Funsol Billing Helper is a simple, straight-forward implementation of the Android v5.1 In-app billing API

Now only subscription supported 

## Getting Started

#### Dependencies

No extra dependencies required

## Step 1

Add maven repository in project level build.gradle or in latest project setting.gradle file

```
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
```  

## Step 2

Add Funsol Billing Helper dependencies in App level build.gradle.

```
    dependencies {
        implementation 'com.github.Funsol-Projects:Funsol-Billing-Helper:1.0'
    }
```  

## Step 3 (Setup)

Finally intialize Billing class and setup Subscription Ids

```
    FunSolBillingHelper(this).setSubKeys(mutableListOf("basic", "standard"))
```
Call this in first stable activity

### Enable Logs


```
    FunSolBillingHelper(this).setSubKeys(mutableListOf("basic", "standard")).enableLogging()
```

### Get all subscriptions prices

return all products prices list

```
    FunSolBillingHelper(this).getAllProductPrices()
```


### Get specfic subscription price


```
    FunSolBillingHelper(this).getProductPriceByKey("Base Plan ID","Offer ID").price
```
This method return ```ProductPriceInfo``` object that conatain complete detail   about subscription. To get only price just call ```.Price```.


### Subscribe to a Subscription

Subscribe to a Subscription
```
    FunSolBillingHelper(this).subscribe(this, "Base Plan ID")
```
Subscribe to a offer
```
    FunSolBillingHelper(this).subscribe(this, "Base Plan ID","Offer ID")
```

Note: it auto acknowledge the susbscription and give callback when product acknowledged sucessfully.

### Upgrade or Downgrade Subscription

 ```
    FunSolBillingHelper(this).upgradeOrDowngradeSubscription(this, "Old Base Plan ID", "Old Offer Id (If offer )", "Old Base Plan ID", ProrationMode)

```
```ProrationMode``` is a setting in subscription billing systems that determines how proration is calculated when changes are made to a subscription plan. There are different proration modes, including:

```
  1. DEFERRED

    Replacement takes effect when the old plan expires, and the new price will be charged at the same time.

  2. IMMEDIATE_AND_CHARGE_FULL_PRICE

    Replacement takes effect immediately, and the user is charged full price of new plan and is given a full billing cycle of subscription, plus remaining prorated time from the old plan.

  3. IMMEDIATE_AND_CHARGE_PRORATED_PRICE

    Replacement takes effect immediately, and the billing cycle remains the same.

  4. IMMEDIATE_WITHOUT_PRORATION

    Replacement takes effect immediately, and the new price will be charged on next recurrence time.

  5. IMMEDIATE_WITH_TIME_PRORATION

    Replacement takes effect immediately, and the remaining time will be prorated and credited to the user.

  6. UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY

```
Example :

```
  FunSolBillingHelper(this).upgradeOrDowngradeSubscription(this, "Old Base Plan ID", "Old Offer Id (If offer )", "Old Base Plan ID", BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE)
 ```

### Billing Listeners

Interface implementation to handle purchase results and errors.
 ```
      FunSolBillingHelper(this).setBillingEventListener(object : BillingEventListener {
            override fun onProductsPurchased(purchases: List<Purchase?>) {
            }

            override fun onPurchaseAcknowledged(purchase: Purchase) {
            }

            override fun onBillingError(error: ErrorType) {
                when (error) {
                    ErrorType.CLIENT_NOT_READY -> {

                    }
                    ErrorType.CLIENT_DISCONNECTED -> {

                    }
                    ErrorType.PRODUCT_NOT_EXIST -> {

                    }
                    ErrorType.BILLING_ERROR -> {

                    }
                    ErrorType.USER_CANCELED -> {

                    }
                    ErrorType.SERVICE_UNAVAILABLE -> {

                    }
                    ErrorType.BILLING_UNAVAILABLE -> {

                    }
                    ErrorType.ITEM_UNAVAILABLE -> {

                    }
                    ErrorType.DEVELOPER_ERROR -> {

                    }
                    ErrorType.ERROR -> {

                    }
                    ErrorType.ITEM_ALREADY_OWNED -> {

                    }
                    ErrorType.ITEM_NOT_OWNED -> {

                    }

                    ErrorType.SERVICE_DISCONNECTED -> {

                    }

                    ErrorType.ACKNOWLEDGE_ERROR -> {

                    }

                    ErrorType.ACKNOWLEDGE_WARNING -> {

                    }
                    
                    ErrorType.OLD_PURCHASE_TOKEN_NOT_FOUND -> {

                    }
                    else -> {

                    }
                }
            }
        })
```
### Check Subscription (if user buy any subscription)

For check if any subscription is subscribe

 ```
  FunSolBillingHelper(this).isPremiumUser()

 ``` 

For check if any specific subscription is subscribe (by Base Plan ID)

``` 
  FunSolBillingHelper(this).isPremiumUserByBasePlanKey("Base Plan ID")

 ``` 
For check if any specific subscription is subscribe (by Subscription ID)

``` 
  FunSolBillingHelper(this).isPremiumUserBySubBaseIDKey("Subscription ID")

 ``` 

### Cancel  Subscription

```
FunSolBillingHelper(this).unsubscribe(this,"Subscription ID")

```


### Check susbscription support

```
FunSolBillingHelper(this).areSubscriptionsSupported()

```


### Release billing client object

Call this method when app close or when billing not needed any more.
```
FunSolBillingHelper(this).release()

```
This Method used for Releasing the client object and save from memory leaks


## License

#### MIT License
#### Copyright (c) 2023 Hannan Shahid

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

