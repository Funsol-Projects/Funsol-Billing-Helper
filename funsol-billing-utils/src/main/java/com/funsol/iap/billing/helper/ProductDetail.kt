package com.funsol.iap.billing.helper

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.funsol.iap.billing.helper.BillingData.allProducts
import com.funsol.iap.billing.helper.BillingData.billingEventListener
import com.funsol.iap.billing.model.ErrorType

class ProductDetail {
	
	/**
	 * Retrieves the details of a specific product (in-app or subscription) based on the provided keys.
	 *
	 * @param productId The key of the product (product ID for in-app or base plan ID for subscriptions).
	 * @param offerId The offer key for subscriptions, optional for in-app products. Defaults to null.
	 * @param productType The type of the product, either "INAPP" or "SUBS".
	 * @return The [ProductDetails] of the product if found, otherwise null.
	 */
	fun getProductDetail(productId: String, offerId: String? = null, productType: String): ProductDetails? {
		// Return error if no products are available
		if (allProducts.isEmpty()) {
			billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
			return null
		}
		
		// Locate product based on type and keys
		val product = allProducts.find { productDetails ->
			if (productDetails.productType == BillingClient.ProductType.SUBS) {
				// Check if any subscription offer matches
				productDetails.subscriptionOfferDetails?.any { subDetails ->
					subDetails.basePlanId == productId && subDetails.offerId == offerId
				} == true
			} else if (productDetails.productType == BillingClient.ProductType.INAPP) {
				// Check if it's an in-app product
				if (productDetails.productId == productId) {
					true
				} else {
					false
				}
			} else {
				false
			}
		}
		
		// Handle missing product
		if (product == null) {
			billingEventListener?.onBillingError(ErrorType.PRODUCT_NOT_EXIST)
		}
		
		return product
	}
	
	/**
	 * Retrieves the offer token for a given product ID and optional offer ID from the provided list of subscription offers.
	 *
	 * @param offerList The list of subscription offer details.
	 * @param basePlanId The base plan ID to match with the base plan ID in the offer details.
	 * @param offerId The offer ID to match with the offer details (optional, default is null).
	 * @return The offer token if a matching offer is found, otherwise an empty string.
	 */
	fun getOfferToken(offerList: List<ProductDetails.SubscriptionOfferDetails>?, basePlanId: String, offerId: String? = null): String {
		offerList?.forEach { product ->
			// Check for a matching offer with basePlanId and offerId (if provided)
			if (product.basePlanId == basePlanId && product.offerId == offerId) {
				return product.offerToken
			}
		}
		// Log and return an empty string if no matching offer is found
		logFunsolBilling("No offer found for basePlanId: $basePlanId and offerId: ${offerId ?: "null"}")
		return ""
	}
	
	fun getProductType(productKey: String): String {
		allProducts.forEach { productDetail ->
			if (productDetail.productType == BillingClient.ProductType.INAPP) {
				if (productDetail.productId == productKey) {
					return productDetail.productType
				}
			} else {
				productDetail.subscriptionOfferDetails?.forEach {
					if (it.basePlanId == productKey) {
						return productDetail.productType
					}
				}
			}
		}
		return ""
	}
}