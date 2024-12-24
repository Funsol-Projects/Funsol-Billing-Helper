package com.funsol.iap.billing.helper

import com.android.billingclient.api.BillingClient
import com.funsol.iap.billing.helper.BillingData.allProducts
import com.funsol.iap.billing.helper.BillingData.billingEventListener
import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.ProductPriceInfo

class ProductPrices {
	
	/**
	 * Retrieves a list of pricing details for all available products.
	 *
	 * This method iterates over `allProducts` and compiles a list of prices for both in-app products
	 * and subscription products. For in-app products, it includes lifetime purchase details. For subscriptions,
	 * it includes details from the first pricing phase of each subscription offer.
	 *
	 * @return A mutable list of [ProductPriceInfo] containing pricing details for each product, or an empty list if an error occurs.
	 */
	fun getAllProductPrices(): MutableList<ProductPriceInfo> {
		val priceList = mutableListOf<ProductPriceInfo>()
		
		try {
			allProducts.forEach { product ->
				if (product.productType == BillingClient.ProductType.INAPP) {
					// Handle in-app product pricing details
					product.oneTimePurchaseOfferDetails?.let { offerDetails ->
						priceList.add(ProductPriceInfo().apply {
							title = product.title
							type = product.productType
							productId = product.productId
							basePlanId = ""
							offerId = ""
							price = offerDetails.formattedPrice ?: ""
							priceMicro = offerDetails.priceAmountMicros ?: 0L
							currencyCode = offerDetails.priceCurrencyCode ?: ""
							duration = "lifeTime"
						})
					}
				} else {
					// Handle subscription product pricing details
					product.subscriptionOfferDetails?.forEach { subDetails ->
						subDetails.pricingPhases.pricingPhaseList.firstOrNull()?.let { pricingPhase ->
							priceList.add(ProductPriceInfo().apply {
								title = product.title
								type = product.productType
								productId = product.productId
								basePlanId = subDetails.basePlanId
								offerId = subDetails.offerId ?: ""
								price = pricingPhase.formattedPrice ?: ""
								priceMicro = pricingPhase.priceAmountMicros ?: 0L
								currencyCode = pricingPhase.priceCurrencyCode ?: ""
								duration = pricingPhase.billingPeriod ?: ""
							})
						}
					}
				}
			}
		} catch (e: Exception) {
			return mutableListOf()
		}
		
		return priceList
	}
	
	
	
	/**
	 * Retrieves the price information for a specific subscription product based on the provided `basePlanId`
	 * and optional `offerId`.
	 *
	 * @param basePlanId The unique identifier of the base plan to look up.
	 * @param offerId An optional identifier for the offer. If `offerId` is provided, the method looks for
	 *                a matching product with both `basePlanId` and `offerId`. If `offerId` is null, it matches
	 *                only based on `basePlanId` with a null `offerId`.
	 * @return A [ProductPriceInfo] object containing the price details if a matching product is found,
	 *         or `null` if no match is found. In case of errors, it returns `null` and logs the issue.
	 */
	fun getSubscriptionProductPriceById(basePlanId: String, offerId: String? = null): ProductPriceInfo? {
		try {
			allProducts.forEach { product ->
				if (product.productType == BillingClient.ProductType.SUBS) {
					product.subscriptionOfferDetails?.forEach { offerDetail ->
						// Match based on basePlanId and offerId if it's not null
						val isOfferMatch = offerId?.let { offerDetail.offerId == it } ?: (offerDetail.offerId == null)
						if (offerDetail.basePlanId == basePlanId && isOfferMatch) {
							return ProductPriceInfo().apply {
								title = product.title
								type = product.productType
								productId = product.productId
								this.basePlanId = offerDetail.basePlanId
								this.offerId = offerDetail.offerId.orEmpty()
								price = offerDetail.pricingPhases.pricingPhaseList.first().formattedPrice
								priceMicro = offerDetail.pricingPhases.pricingPhaseList.first().priceAmountMicros ?: 0L
								currencyCode = offerDetail.pricingPhases.pricingPhaseList.first().priceCurrencyCode.toString()
								duration = offerDetail.pricingPhases.pricingPhaseList.first().billingPeriod
							}
						}
					}
				}
			}
		} catch (e: Exception) {
			// Handle any potential exceptions quietly, logging will occur outside of this block
		}
		
		logFunsolBilling("SUBS Product Price not found for basePlanId = $basePlanId, offerId = $offerId, because product is missing")
		billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
		return null
	}
	
	
	
	/**
	 * Retrieves the pricing information for a specific in-app product based on its product ID.
	 *
	 * This method iterates over `allProducts` to find a matching in-app product (`ProductType.INAPP`)
	 * with the specified `inAppProductId`. If found, it returns the product's price details, otherwise `null`.
	 *
	 * @param inAppProductId The product ID of the in-app product to retrieve.
	 * @return A [ProductPriceInfo] object containing the in-app product's price information if found, or `null` if no match is found.
	 */
	fun getInAppProductPriceById(inAppProductId: String): ProductPriceInfo? {
		try {
			allProducts.forEach { product ->
				if (product.productType == BillingClient.ProductType.INAPP && product.productId == inAppProductId) {
					return ProductPriceInfo().apply {
						title = product.title
						type = product.productType
						productId = product.productId
						basePlanId = ""
						offerId = ""
						product.oneTimePurchaseOfferDetails?.let { offerDetails ->
							price = offerDetails.formattedPrice ?: ""
							priceMicro = offerDetails.priceAmountMicros ?: 0L
							currencyCode = offerDetails.priceCurrencyCode ?: ""
						}
						duration = "lifeTime"
					}
				}
			}
		} catch (e: Exception) {
			// Log and handle any errors gracefully
		}
		
		logFunsolBilling("IN-APP Product Price not found because product is missing")
		billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
		return null
	}
}