package com.funsol.iap.billing.helper

import com.android.billingclient.api.BillingClient
import com.funsol.iap.billing.helper.BillingData.allProducts
import com.funsol.iap.billing.helper.BillingData.billingEventListener
import com.funsol.iap.billing.model.ErrorType
import com.funsol.iap.billing.model.ProductPriceInfo
import java.util.Currency

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
                            price = offerDetails.formattedPrice
                            priceMicro = offerDetails.priceAmountMicros
                            currencyCode = offerDetails.priceCurrencyCode
                            currencySymbol = getCurrencySymbol(currencyCode = offerDetails.priceCurrencyCode)
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
                                price = pricingPhase.formattedPrice
                                priceMicro = pricingPhase.priceAmountMicros
                                currencyCode = pricingPhase.priceCurrencyCode
                                currencySymbol = getCurrencySymbol(currencyCode = pricingPhase.priceCurrencyCode)
                                duration = pricingPhase.billingPeriod
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
                                priceMicro = offerDetail.pricingPhases.pricingPhaseList.first().priceAmountMicros
                                currencyCode = offerDetail.pricingPhases.pricingPhaseList.first().priceCurrencyCode
                                currencySymbol = getCurrencySymbol(currencyCode = offerDetail.pricingPhases.pricingPhaseList.first().priceCurrencyCode)
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
                            price = offerDetails.formattedPrice
                            priceMicro = offerDetails.priceAmountMicros
                            currencyCode = offerDetails.priceCurrencyCode
                            currencySymbol = getCurrencySymbol(currencyCode = offerDetails.priceCurrencyCode)
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


    private fun getCurrencySymbol(currencyCode: String): String {
        return customCurrencySymbols[currencyCode] ?: currencyCode
    }

    val customCurrencySymbols = mapOf(
        "AED" to "د.إ",   // UAE Dirham
        "AFN" to "؋",     // Afghan Afghani
        "ALL" to "L",     // Albanian Lek
        "AMD" to "֏",     // Armenian Dram
        "ANG" to "ƒ",     // Netherlands Antillean Guilder
        "AOA" to "Kz",    // Angolan Kwanza
        "ARS" to "$",     // Argentine Peso
        "AUD" to "A$",    // Australian Dollar
        "AWG" to "ƒ",     // Aruban Florin
        "AZN" to "₼",     // Azerbaijani Manat
        "BAM" to "KM",    // Bosnia-Herzegovina Convertible Mark
        "BBD" to "Bds$",  // Barbadian Dollar
        "BDT" to "৳",     // Bangladeshi Taka
        "BGN" to "лв",    // Bulgarian Lev
        "BHD" to ".د.ب",  // Bahraini Dinar
        "BIF" to "FBu",   // Burundian Franc
        "BND" to "B$",    // Brunei Dollar
        "BOB" to "Bs.",   // Bolivian Boliviano
        "BRL" to "R$",    // Brazilian Real
        "BSD" to "B$",    // Bahamian Dollar
        "BTN" to "Nu.",   // Bhutanese Ngultrum
        "BWP" to "P",     // Botswanan Pula
        "BYN" to "Br",    // Belarusian Ruble
        "BZD" to "BZ$",   // Belize Dollar
        "CAD" to "C$",    // Canadian Dollar
        "CDF" to "FC",    // Congolese Franc
        "CHF" to "CHF",   // Swiss Franc
        "CLP" to "$",     // Chilean Peso
        "CNY" to "¥",     // Chinese Yuan
        "COP" to "$",     // Colombian Peso
        "CRC" to "₡",     // Costa Rican Colón
        "CUP" to "₱",     // Cuban Peso
        "CVE" to "$",     // Cape Verdean Escudo
        "CZK" to "Kč",    // Czech Koruna
        "DJF" to "Fdj",   // Djiboutian Franc
        "DKK" to "kr",    // Danish Krone
        "DOP" to "RD$",   // Dominican Peso
        "DZD" to "د.ج",   // Algerian Dinar
        "EGP" to "£",     // Egyptian Pound
        "ERN" to "Nfk",   // Eritrean Nakfa
        "ETB" to "Br",    // Ethiopian Birr
        "EUR" to "€",     // Euro
        "FJD" to "FJ$",   // Fijian Dollar
        "FKP" to "£",     // Falkland Islands Pound
        "FOK" to "kr",    // Faroese Króna
        "GBP" to "£",     // British Pound
        "GEL" to "₾",     // Georgian Lari
        "GHS" to "₵",     // Ghanaian Cedi
        "GIP" to "£",     // Gibraltar Pound
        "GMD" to "D",     // Gambian Dalasi
        "GNF" to "FG",    // Guinean Franc
        "GTQ" to "Q",     // Guatemalan Quetzal
        "GYD" to "G$",    // Guyanese Dollar
        "HKD" to "HK$",   // Hong Kong Dollar
        "HNL" to "L",     // Honduran Lempira
        "HRK" to "kn",    // Croatian Kuna
        "HTG" to "G",     // Haitian Gourde
        "HUF" to "Ft",    // Hungarian Forint
        "IDR" to "Rp",    // Indonesian Rupiah
        "ILS" to "₪",     // Israeli New Shekel
        "INR" to "₹",     // Indian Rupee
        "IQD" to "ع.د",   // Iraqi Dinar
        "IRR" to "﷼",     // Iranian Rial
        "ISK" to "kr",    // Icelandic Króna
        "JMD" to "J$",    // Jamaican Dollar
        "JOD" to "د.ا",   // Jordanian Dinar
        "JPY" to "¥",     // Japanese Yen
        "KES" to "KSh",   // Kenyan Shilling
        "KGS" to "лв",    // Kyrgyzstani Som
        "KHR" to "៛",     // Cambodian Riel
        "KMF" to "CF",    // Comorian Franc
        "KPW" to "₩",     // North Korean Won
        "KRW" to "₩",     // South Korean Won
        "KWD" to "د.ك",   // Kuwaiti Dinar
        "KYD" to "CI$",   // Cayman Islands Dollar
        "KZT" to "₸",     // Kazakhstani Tenge
        "LAK" to "₭",     // Lao Kip
        "LBP" to "ل.ل",   // Lebanese Pound
        "LKR" to "Rs",    // Sri Lankan Rupee
        "LRD" to "L$",    // Liberian Dollar
        "LSL" to "M",     // Lesotho Loti
        "LYD" to "ل.د",   // Libyan Dinar
        "MAD" to "د.م.",  // Moroccan Dirham
        "MDL" to "L",     // Moldovan Leu
        "MGA" to "Ar",    // Malagasy Ariary
        "MKD" to "ден",   // Macedonian Denar
        "MMK" to "K",     // Myanmar Kyat
        "MNT" to "₮",     // Mongolian Tögrög
        "MOP" to "MOP$",  // Macanese Pataca
        "MRU" to "UM",    // Mauritanian Ouguiya
        "MUR" to "Rs",     // Mauritian Rupee
        "MVR" to "Rf",    // Maldivian Rufiyaa
        "MWK" to "MK",    // Malawian Kwacha
        "MXN" to "$",     // Mexican Peso
        "MYR" to "RM",    // Malaysian Ringgit
        "MZN" to "MT",    // Mozambican Metical
        "NAD" to "N$",    // Namibian Dollar
        "NGN" to "₦",     // Nigerian Naira
        "NIO" to "C$",    // Nicaraguan Córdoba
        "NOK" to "kr",    // Norwegian Krone
        "NPR" to "Rs",     // Nepalese Rupee
        "NZD" to "NZ$",   // New Zealand Dollar
        "OMR" to "﷼",     // Omani Rial
        "PAB" to "B/.",   // Panamanian Balboa
        "PEN" to "S/",    // Peruvian Sol
        "PGK" to "K",     // Papua New Guinean Kina
        "PHP" to "₱",     // Philippine Peso
        "PKR" to "Rs",     // Pakistani Rupee
        "PLN" to "zł",    // Polish Złoty
        "PYG" to "₲",     // Paraguayan Guarani
        "QAR" to "﷼",     // Qatari Rial
        "RON" to "lei",   // Romanian Leu
        "RSD" to "дин",   // Serbian Dinar
        "RUB" to "₽",     // Russian Ruble
        "RWF" to "FRw",   // Rwandan Franc
        "SAR" to "﷼",     // Saudi Riyal
        "SBD" to "SI$",   // Solomon Islands Dollar
        "SCR" to "Rs",     // Seychellois Rupee
        "SDG" to "ج.س.", // Sudanese Pound
        "SEK" to "kr",    // Swedish Krona
        "SGD" to "S$",    // Singapore Dollar
        "SHP" to "£",     // Saint Helena Pound
        "SLL" to "Le",    // Sierra Leonean Leone
        "SOS" to "Sh",    // Somali Shilling
        "SRD" to "SR$",   // Surinamese Dollar
        "SSP" to "£",     // South Sudanese Pound
        "STN" to "Db",    // São Tomé and Príncipe Dobra
        "SYP" to "£",     // Syrian Pound
        "SZL" to "E",     // Swazi Lilangeni
        "THB" to "฿",     // Thai Baht
        "TJS" to "SM", "TMT" to "m",     // Turkmenistani Manat
        "TND" to "د.ت",   // Tunisian Dinar
        "TOP" to "T$",    // Tongan Paʻanga
        "TRY" to "₺",     // Turkish Lira
        "TTD" to "TT$",   // Trinidad and Tobago Dollar
        "TWD" to "NT$",   // New Taiwan Dollar
        "TZS" to "Sh",    // Tanzanian Shilling
        "UAH" to "₴",     // Ukrainian Hryvnia
        "UGX" to "USh",   // Ugandan Shilling
        "USD" to "$",     // United States Dollar
        "UYU" to "\u0024U",    // Uruguayan Peso
        "UZS" to "лв",    // Uzbekistani Som
        "VES" to "Bs.",   // Venezuelan Bolívar Soberano
        "VND" to "₫",     // Vietnamese Dong
        "VUV" to "VT",    // Vanuatu Vatu
        "WST" to "T",     // Samoan Tala
        "XAF" to "FCFA",  // Central African CFA Franc
        "XCD" to "EC$",   // East Caribbean Dollar
        "XOF" to "CFA",   // West African CFA Franc
        "XPF" to "₣",     // CFP Franc
        "YER" to "﷼",     // Yemeni Rial
        "ZAR" to "R",     // South African Rand
        "ZMW" to "K",     // Zambian Kwacha
        "ZWL" to "Z$",    // Zimbabwean Dollar
    )
}