package plugin.amazon.iap.v3;

import com.amazon.device.drm.model.LicenseResponse;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.ProductType;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;

import com.naef.jnlua.LuaRuntimeException;
import com.naef.jnlua.LuaState;

import java.util.Hashtable;

public class Utils {
    static void checkArgCount(LuaState L, int count) {
        if (L.getTop() != count) {
            throw new LuaRuntimeException("This function requires " + count + " arguments.");
        }
    }

    static void checkIsActive(LuaLoader luaLoader) {
        if (!luaLoader.isActive) {
            throw new LuaRuntimeException("You have not correctly initialized the Amazon IAP Plugin. Call init(storeListener) first.");
        }
    }

	static String productTypeToString(ProductType productType) {
		switch (productType) {
			case CONSUMABLE:
				return "CONSUMABLE";
			case ENTITLED:
				return "ENTITLEMENT";
			case SUBSCRIPTION:
				return "SUBSCRIPTION";
			default:
				return "";
		}
	}

    static String responseStatusToString(UserDataResponse.RequestStatus status) {
        switch (status) {
            case FAILED:
                return "failed";
            case NOT_SUPPORTED:
                return "not supported";
            case SUCCESSFUL:
                return "successful";
            default:
                return "";
        }
    }

    static String responseStatusToString(ProductDataResponse.RequestStatus status) {
        switch (status) {
            case FAILED:
                return "failed";
            case NOT_SUPPORTED:
                return "not supported";
            case SUCCESSFUL:
                return "successful";
            default:
                return "";
        }
    }

    static String responseStatusToString(PurchaseResponse.RequestStatus status) {
        switch (status) {
            case ALREADY_PURCHASED:
                return "already purchased";
            case FAILED:
                return "failed";
            case INVALID_SKU:
                return "invalid sku";
            case NOT_SUPPORTED:
                return "not supported";
            case SUCCESSFUL:
                return "successful";
            default:
                return "";
        }
    }

    static String responseStatusToString(PurchaseUpdatesResponse.RequestStatus status) {
        switch (status) {
            case FAILED:
                return "failed";
            case NOT_SUPPORTED:
                return "not supported";
            case SUCCESSFUL:
                return "successful";
            default:
                return "";
        }
    }
    static String responseStatusToString(LicenseResponse.RequestStatus status) {
        switch (status) {
            case LICENSED:
                return "Licensed";
            case NOT_LICENSED:
                return "Not licensed";
            case ERROR_VERIFICATION:
                return "Error with verification";
            case ERROR_INVALID_LICENSING_KEYS:
                return "Invalid licensing keys";
            case EXPIRED:
                return "Expired";
            case UNKNOWN_ERROR:
                return "Unknown error";
            default:
                return "";
        }
    }

    static Hashtable<String, Object> formTransaction(String state, Receipt receipt, UserData userData) {
        Hashtable<String, Object> transaction = new Hashtable<>();
        transaction.put("state", state);

        if (receipt != null) {
            transaction.put("receipt", receipt.toJSON().toString());
			transaction.put("identifier", receipt.getReceiptId());
            transaction.put("productIdentifier", receipt.getSku());
            transaction.put("type", productTypeToString(receipt.getProductType()));
			transaction.put("date", String.valueOf(receipt.getPurchaseDate().getTime()));
            if (receipt.getCancelDate() != null) {
                transaction.put("cancelDate", String.valueOf(receipt.getCancelDate().getTime()));
            }
			if (receipt.getProductType() == ProductType.SUBSCRIPTION) {
				transaction.put("subscriptionStartDate", String.valueOf(receipt.getPurchaseDate().getTime()));
				if (receipt.getCancelDate() != null) {
					transaction.put("subscriptionEndDate", String.valueOf(receipt.getCancelDate().getTime()));
				}
			}
        }

        if (userData != null) {
			if (userData.getUserId() != null) {
				transaction.put("userId", userData.getUserId());
			}
			if (userData.getMarketplace() != null) {
				transaction.put("marketplace", userData.getMarketplace());
			}
		}

        return transaction;
    }
}
