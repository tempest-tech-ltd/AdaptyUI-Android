package com.adapty.ui.internal

import android.view.View
import android.widget.TextView
import androidx.annotation.RestrictTo
import com.adapty.models.AdaptyPaywallProduct
import com.adapty.models.AdaptyPeriodUnit
import com.adapty.models.AdaptyProductDiscountPhase.PaymentMode
import com.adapty.models.AdaptyViewConfiguration.Component
import java.math.RoundingMode

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class Products(
    val products: List<ProductInfo>,
    val blockType: BlockType,
) {
    sealed class BlockType {
        object Single: BlockType()

        object Vertical: Multiple()

        object Horizontal: Multiple()

        sealed class Multiple: BlockType()
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ProductInfo(
    val title: Component.Text?,
    private val subtitleDefault: Component.Text?,
    private val subtitlePayUpfront: Component.Text?,
    private val subtitlePayAsYouGo: Component.Text?,
    private val subtitleFreeTrial: Component.Text?,
    val secondTitle: Component.Text?,
    val secondSubtitle: Component.Text?,
    val button: Component.Button?,
    val tagText: Component.Text?,
    val tagShape: Component.Shape?,
) {

    fun getSubtitle(product: AdaptyPaywallProduct): Component.Text? {
        return when(product.firstDiscountOfferOrNull()?.paymentMode) {
            PaymentMode.FREE_TRIAL -> subtitleFreeTrial
            PaymentMode.PAY_AS_YOU_GO -> subtitlePayAsYouGo
            PaymentMode.PAY_UPFRONT -> subtitlePayUpfront
            else -> subtitleDefault
        } ?: subtitleDefault
    }

    val hasSubtitle: Boolean get() = (subtitleDefault ?: subtitlePayUpfront ?: subtitlePayAsYouGo ?: subtitleFreeTrial) != null

    companion object {
        fun from(map: Map<String, Component>, isMainProduct: Boolean): ProductInfo {
            return ProductInfo(
                title = map["title"] as? Component.Text,
                subtitleDefault = map["subtitle"] as? Component.Text,
                subtitlePayUpfront = map["subtitle_payupfront"] as? Component.Text,
                subtitlePayAsYouGo = map["subtitle_payasyougo"] as? Component.Text,
                subtitleFreeTrial = map["subtitle_freetrial"] as? Component.Text,
                secondTitle = map["second_title"] as? Component.Text,
                secondSubtitle = map["second_subtitle"] as? Component.Text,
                button = map["button"] as? Component.Button,
                tagText = (map["tag_text"] as? Component.Text)?.takeIf { isMainProduct },
                tagShape = (map["tag_shape"] as? Component.Shape)?.takeIf { isMainProduct },
            )
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class ProductViewsBundle(
    val productCell: View?,
    val productTitle: TextView?,
    val productSubtitle: TextView?,
    val productSecondTitle: TextView?,
    val productSecondSubtitle: TextView?,
    val mainProductTag: TextView?,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal sealed class ProductPlaceholderContentData(
    val placeholder: String,
) {
    class Simple(placeholder: String, val value: String): ProductPlaceholderContentData(placeholder)

    class Extended(placeholder: String, val value: String, product: AdaptyPaywallProduct): ProductPlaceholderContentData(placeholder) {
        val currencyCode = product.price.currencyCode
        val currencySymbol = product.price.currencySymbol
    }

    class Drop(placeholder: String): ProductPlaceholderContentData(placeholder)

    companion object {

        fun create(product: AdaptyPaywallProduct): List<ProductPlaceholderContentData> {
            val firstDiscountOfferIfExists = product.firstDiscountOfferOrNull()

            return listOf(
                from("</TITLE/>", product.localizedTitle),
                from("</PRICE/>", product.price.localizedString, product),
                from("</PRICE_PER_DAY/>", createPricePerPeriodText(product, AdaptyPeriodUnit.DAY), product),
                from("</PRICE_PER_WEEK/>", createPricePerPeriodText(product, AdaptyPeriodUnit.WEEK), product),
                from("</PRICE_PER_MONTH/>", createPricePerPeriodText(product,
                    AdaptyPeriodUnit.MONTH
                ), product),
                from("</PRICE_PER_YEAR/>", createPricePerPeriodText(product, AdaptyPeriodUnit.YEAR), product),
                from("</OFFER_PRICE/>", firstDiscountOfferIfExists?.price?.localizedString, product),
                from("</OFFER_PERIOD/>", firstDiscountOfferIfExists?.localizedSubscriptionPeriod),
                from("</OFFER_NUMBER_OF_PERIOD/>", firstDiscountOfferIfExists?.localizedNumberOfPeriods),
            )
        }

        private fun from(placeholder: String, value: String?, product: AdaptyPaywallProduct? = null): ProductPlaceholderContentData =
            when {
                value == null -> Drop(placeholder)
                product == null -> Simple(placeholder, value)
                else -> Extended(placeholder, value, product)
            }

        private fun createPricePerPeriodText(product: AdaptyPaywallProduct, targetUnit: AdaptyPeriodUnit): String? {
            val subscriptionPeriod = product.subscriptionDetails?.subscriptionPeriod
            val price = product.price
            val unit =
                subscriptionPeriod?.unit?.takeIf { it in listOf(
                    AdaptyPeriodUnit.WEEK,
                    AdaptyPeriodUnit.YEAR,
                    AdaptyPeriodUnit.MONTH
                ) } ?: return null
            val numberOfUnits = subscriptionPeriod.numberOfUnits.takeIf { it > 0 } ?: return null
            val localizedPrice = price.localizedString

            return when {
                unit == targetUnit && numberOfUnits == 1 -> localizedPrice
                else -> {
                    val pricePerPeriod = when (unit) {
                        targetUnit -> price.amount.divide(
                            numberOfUnits.toBigDecimal(),
                            4,
                            RoundingMode.CEILING
                        )

                        else -> {
                            val divisor = (when (unit) {
                                AdaptyPeriodUnit.YEAR -> 365
                                AdaptyPeriodUnit.MONTH -> 30
                                else -> 7
                            } * numberOfUnits).toBigDecimal()

                            val multiplier = (when (targetUnit) {
                                AdaptyPeriodUnit.YEAR -> 365
                                AdaptyPeriodUnit.MONTH -> 30
                                else -> 7
                            }).toBigDecimal()

                            price.amount.divide(divisor, 4, RoundingMode.CEILING) * multiplier
                        }
                    }

                    val pricePerPeriodString =
                        pricePerPeriod
                            .setScale(2, RoundingMode.CEILING)
                            .toPlainString()
                    var startIndex = -1
                    var endIndex = -1
                    for ((i, ch) in localizedPrice.withIndex()) {
                        if (ch.isDigit()) {
                            if (startIndex == -1) startIndex = i
                            endIndex = i
                        }
                    }
                    if (startIndex > -1 && endIndex in startIndex until localizedPrice.length) {
                        localizedPrice.replace(
                            localizedPrice.substring(startIndex..endIndex),
                            pricePerPeriodString
                        )
                    } else {
                        pricePerPeriodString
                    }
                }
            }
        }
    }
}