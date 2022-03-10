package plugin.amazon.iap.v3;

import com.amazon.device.drm.LicensingListener;
import com.amazon.device.drm.LicensingService;
import com.amazon.device.drm.model.AppstoreSDKModes;
import com.amazon.device.drm.model.LicenseResponse;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.FulfillmentResult;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;

import com.ansca.corona.CoronaActivity;
import com.ansca.corona.CoronaEnvironment;
import com.ansca.corona.CoronaLua;
import com.ansca.corona.CoronaRuntime;
import com.ansca.corona.CoronaRuntimeListener;
import com.ansca.corona.CoronaRuntimeTask;

import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;
import com.naef.jnlua.LuaType;
import com.naef.jnlua.NamedJavaFunction;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;

public class LuaLoader implements JavaFunction, CoronaRuntimeListener, PurchasingListener {
	protected boolean isActive;
	private String currentUserId;
	private int luaPlugin = CoronaLua.REFNIL;
	private int luaStoreListener = CoronaLua.REFNIL;
	private int luaUserDataListener = CoronaLua.REFNIL;
	private int luaLoadProductsListener = CoronaLua.REFNIL;
	private int luaLicensingistener = CoronaLua.REFNIL;

	//region CoronaRuntimeListener methods
	@Override
	public void onLoaded(CoronaRuntime runtime) {
	}

	@Override
	public void onStarted(CoronaRuntime runtime) {
	}

	@Override
	public void onSuspended(CoronaRuntime runtime) {
	}

	@Override
	public void onResumed(CoronaRuntime runtime) {
		if (isActive) {
			PurchasingService.getPurchaseUpdates(false);
			PurchasingService.getUserData();
		}
	}

	@Override
	public void onExiting(CoronaRuntime runtime) {
	}
	//endregion

	//region Lua functions
	// require('plugin.amazon.iap')
	public int invoke(LuaState L) {
		NamedJavaFunction[] luaFunctions = new NamedJavaFunction[]{
			new InitWrapper(),
			new IsSandboxModeWrapper(),
			new GetUserIdWrapper(),
			new GetUserDataWrapper(),
			new LoadProductsWrapper(),
			new PurchaseWrapper(),
			new RestoreWrapper(),
			new FinishTransactionWrapper(),
			new VerifyDRMWrapper(),
		};

		String luaPluginName = L.toString(1);
		L.register(luaPluginName, luaFunctions);

		L.pushValue(-1);
		luaPlugin = L.ref(LuaState.REGISTRYINDEX);

		Hashtable<Object, Object> availableStores = new Hashtable<>();
		availableStores.put(1, "amazon");
		CoronaLua.pushHashtable(L, availableStores);
		L.setField(-2, "availableStores");

		L.pushString("amazon");
		L.setField(-2, "target");

		L.pushBoolean(false);
		L.setField(-2, "canLoadProducts");

		L.pushBoolean(false);
		L.setField(-2, "canMakePurchases");

		L.pushBoolean(false);
		L.setField(-2, "isActive");

		return 1;
	}

	// store.init([store], storeListener)
	public int init(LuaState L) {
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity == null) {
			// An exception is disproportionate here (activity may be nil during applicationExit)
			// but unfortunately this API doesn't allow for an error return (store.isActive will
			// be left set to false)
			return 0;
		}

		int luaStoreListenerIndex = 1;
		if (L.type(luaStoreListenerIndex) == LuaType.STRING) {
			luaStoreListenerIndex++;
		}

		if (CoronaLua.isListener(L, luaStoreListenerIndex, "storeTransaction")) {
			luaStoreListener = CoronaLua.newRef(L, luaStoreListenerIndex);
		}

		//We need to check drm to use isSandbox
		final PurchasingListener purchaseListener = this;
		final Semaphore mutex = new Semaphore(0);//used to make sure everything is called first in sync
		Runnable activityRunnable = new Runnable() {
			public void run() {
				PurchasingService.registerListener(activity, purchaseListener);
				PurchasingService.getPurchaseUpdates(false);
				PurchasingService.getUserData();
				LicensingService.verifyLicense(CoronaEnvironment.getCoronaActivity(), new LicensingListener() {
					@Override
					public void onLicenseCommandResponse(final LicenseResponse licenseResponse) {}
				});
				mutex.release();
			}
		};

		if (activity != null) {
			activity.runOnUiThread(activityRunnable);
			try {
				mutex.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}




		isActive = true;
		L.rawGet(LuaState.REGISTRYINDEX, luaPlugin);

		L.pushBoolean(isActive);
		L.setField(-2, "canLoadProducts");

		L.pushBoolean(isActive);
		L.setField(-2, "canMakePurchases");

		L.pushBoolean(isActive);
		L.setField(-2, "isActive");

		L.pop(L.getTop());

		return 0;
	}

	// store.isSandboxMode()
	public int isSandboxMode(LuaState L) {
		Utils.checkIsActive(this);
		if(LicensingService.getAppstoreSDKMode().equals(AppstoreSDKModes.SANDBOX)){
			L.pushBoolean(true);
		}else{
			L.pushBoolean(false);
		}

		return 1;
	}

	// store.getUserId()
	public int getUserId(LuaState L) {
		if (isActive) {
			if (currentUserId != null) {
				L.pushString(currentUserId);
			} else {
				L.pushString("unknown");
			}
		} else {
			L.pushNil();
		}
		return 1;
	}

	// store.getUserData(callback)
	public int getUserData(LuaState L) {
		Utils.checkIsActive(this);
		Utils.checkArgCount(L, 1);

		if (CoronaLua.isListener(L, 1, "userData")) {
			luaUserDataListener = CoronaLua.newRef(L, 1);
		}

		PurchasingService.getUserData();
		return 0;
	}

	// store.loadProducts(skus, listener)
	public int loadProducts(LuaState L) {
		Utils.checkIsActive(this);
		Utils.checkArgCount(L, 2);
		L.checkType(1, LuaType.TABLE);

		if (CoronaLua.isListener(L, 2, "productList")) {
			luaLoadProductsListener = CoronaLua.newRef(L, 2);
		}

		HashSet<String> products = new HashSet<>();
		L.pushNil(); // Without this line - crash on L.next(), not sure what it does
		while (L.next(1)) {
			String sku = L.toString(-1);
			products.add(sku);
			L.pop(1);
		}

		PurchasingService.getProductData(products);
		return 0;
	}

	// store.purchase(sku)
	public int purchase(LuaState L) {
		Utils.checkIsActive(this);
		Utils.checkArgCount(L, 1);
		String sku = L.checkString(1);
		PurchasingService.purchase(sku);
		return 0;
	}

	// store.restore()
	public int restore(LuaState L) {
		Utils.checkIsActive(this);
		Utils.checkArgCount(L, 0);
		PurchasingService.getPurchaseUpdates(true);
		return 0;
	}

	// store.finishTransaction(transaction)
	public int finishTransaction(LuaState L) {
		Utils.checkIsActive(this);
		Utils.checkArgCount(L, 1);
		if (L.isTable(1)) {
			L.getField(1, "identifier");
			String receiptId = L.toString(-1);
			L.pop(1);

			if (receiptId != null) {
				PurchasingService.notifyFulfillment(receiptId, FulfillmentResult.FULFILLED);
			}
		}

		L.pop(L.getTop());
		return 0;
	}
	//store.verify
	public int verifyDRM(LuaState L) {
		Utils.checkIsActive(this);

		if (CoronaLua.isListener(L, 1, "licensing")) {
			luaLicensingistener = CoronaLua.newRef(L, 1);
		}
		CoronaRuntimeTask task = new CoronaRuntimeTask() {
			public void executeUsing(CoronaRuntime runtime) {
				LicensingService.verifyLicense(CoronaEnvironment.getApplicationContext(), new LicensingListener() {
					@Override
					public void onLicenseCommandResponse(final LicenseResponse licenseResponse) {
							if (luaLicensingistener == CoronaLua.REFNIL) {
								return;
							}
							LuaState L = runtime.getLuaState();
							Hashtable<Object, Object> event = new Hashtable<>();
							event.put("name", "licensing");
							event.put("provider", "amazaon");
							if (licenseResponse.getRequestStatus() == LicenseResponse.RequestStatus.LICENSED) {
								event.put("isError", false);
								event.put("isVerified", true);
								event.put("response", Utils.responseStatusToString(licenseResponse.getRequestStatus()));
								CoronaLua.pushHashtable(L, event);
								try {
									CoronaLua.dispatchEvent(L, luaLicensingistener, 0);
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							} else {
								event.put("isError", true);
								event.put("isVerified", false);
								event.put("response", Utils.responseStatusToString(licenseResponse.getRequestStatus()));

								CoronaLua.pushHashtable(L, event);
								try {
									CoronaLua.dispatchEvent(L, luaLicensingistener, 0);
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							}

						}

				});
			}
		};
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			activity.getRuntimeTaskDispatcher().send(task);
		}

		return 0;
	}
	//endregion

	//region PurchaseListener methods
	public void onUserDataResponse(final UserDataResponse response) {
		CoronaRuntimeTask task = new CoronaRuntimeTask() {
			public void executeUsing(CoronaRuntime runtime) {
				Hashtable<Object, Object> event = new Hashtable<>();
				event.put("name", "userData");

				if (response.getRequestStatus() == UserDataResponse.RequestStatus.SUCCESSFUL) {
					event.put("isError", false);
					UserData userData = response.getUserData();
					if (userData != null) {
						if (userData.getUserId() != null) {
							currentUserId = userData.getUserId();
							event.put("userId", currentUserId);
						}
						if (userData.getMarketplace() != null) {
							event.put("marketplace", userData.getMarketplace());
						}
					}
				} else {
					event.put("isError", true);
					event.put("errorType", response.getRequestStatus().toString());
					event.put("errorString", Utils.responseStatusToString(response.getRequestStatus()));
				}

				if (luaUserDataListener == CoronaLua.REFNIL) {
					return;
				}

				LuaState L = runtime.getLuaState();
				CoronaLua.pushHashtable(L, event);

				try {
					CoronaLua.dispatchEvent(L, luaUserDataListener, 0);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				CoronaLua.deleteRef(L, luaUserDataListener);
				luaUserDataListener = CoronaLua.REFNIL;
			}
		};
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			activity.getRuntimeTaskDispatcher().send(task);
		}
	}

	public void onProductDataResponse(final ProductDataResponse response) {
		CoronaRuntimeTask task = new CoronaRuntimeTask() {
			public void executeUsing(CoronaRuntime runtime) {
				if (luaLoadProductsListener == CoronaLua.REFNIL) {
					return;
				}
				Hashtable<Object, Object> event = new Hashtable<>();
				event.put("name", "productList");

				if (response.getRequestStatus() == ProductDataResponse.RequestStatus.SUCCESSFUL) {
					event.put("isError", false);
					Hashtable<Object, Object> products = new Hashtable<>();
					int i = 1;
					for (Product p : response.getProductData().values()) {
						Hashtable<String, String> product = new Hashtable<>();
						product.put("title", p.getTitle());
						product.put("description", p.getDescription());
						product.put("price", p.getPrice());
						product.put("localizedPrice", p.getPrice());
						product.put("productIdentifier", p.getSku());
						product.put("type", Utils.productTypeToString(p.getProductType()));
						product.put("smallIconUrl", p.getSmallIconUrl());
						products.put(i, product);
						i++;
					}
					event.put("products", products);

					Hashtable<Object, Object> invalidProducts = new Hashtable<>();
					i = 1;
					for (String sku : response.getUnavailableSkus()) {
						invalidProducts.put(i, sku);
						i++;
					}
					event.put("invalidProducts", invalidProducts);
				} else {
					event.put("isError", true);
					event.put("errorType", response.getRequestStatus().toString());
					event.put("errorString", Utils.responseStatusToString(response.getRequestStatus()));
				}

				LuaState L = runtime.getLuaState();
				CoronaLua.pushHashtable(L, event);

				try {
					CoronaLua.dispatchEvent(L, luaLoadProductsListener, 0);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				CoronaLua.deleteRef(L, luaLoadProductsListener);
				luaLoadProductsListener = CoronaLua.REFNIL;
			}
		};
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			activity.getRuntimeTaskDispatcher().send(task);
		}
	}

	public void onPurchaseResponse(final PurchaseResponse response) {
		CoronaRuntimeTask task = new CoronaRuntimeTask() {
			public void executeUsing(CoronaRuntime runtime) {
				if (luaStoreListener == CoronaLua.REFNIL) {
					return;
				}
				Hashtable<Object, Object> event = new Hashtable<>();
				event.put("name", "storeTransaction");

				if (response.getRequestStatus() == PurchaseResponse.RequestStatus.SUCCESSFUL) {
					event.put("isError", false);
					event.put("transaction", Utils.formTransaction("purchased", response.getReceipt(), response.getUserData()));
				} else {
					event.put("isError", true);
					event.put("errorType", response.getRequestStatus().toString());
					event.put("errorString", Utils.responseStatusToString(response.getRequestStatus()));
					event.put("transaction", Utils.formTransaction("failed", null, null));
				}

				LuaState L = runtime.getLuaState();
				CoronaLua.pushHashtable(L, event);
				try {
					CoronaLua.dispatchEvent(L, luaStoreListener, 0);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			activity.getRuntimeTaskDispatcher().send(task);
		}
	}

	public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
		CoronaRuntimeTask task = new CoronaRuntimeTask() {
			public void executeUsing(CoronaRuntime runtime) {
				if (luaStoreListener == CoronaLua.REFNIL) {
					return;
				}
				LuaState L = runtime.getLuaState();
				Hashtable<Object, Object> event = new Hashtable<>();
				event.put("name", "storeTransaction");

				if (response.getRequestStatus() == PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
					event.put("isError", false);
					for (Receipt receipt : response.getReceipts()) {
						if (receipt.isCanceled()) {
							event.put("transaction", Utils.formTransaction("cancelled", receipt, response.getUserData()));
						} else {
							event.put("transaction", Utils.formTransaction("purchased", receipt, response.getUserData()));
						}

						CoronaLua.pushHashtable(L, event);
						try {
							CoronaLua.dispatchEvent(L, luaStoreListener, 0);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				} else {
					event.put("isError", true);
					event.put("errorType", response.getRequestStatus().toString());
					event.put("errorString", Utils.responseStatusToString(response.getRequestStatus()));
					event.put("transaction", Utils.formTransaction("failed", null, null));

					CoronaLua.pushHashtable(L, event);
					try {
						CoronaLua.dispatchEvent(L, luaStoreListener, 0);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}

				if(response.hasMore()) {
					PurchasingService.getPurchaseUpdates(false);
				}
			}
		};
		CoronaActivity activity = CoronaEnvironment.getCoronaActivity();
		if (activity != null) {
			activity.getRuntimeTaskDispatcher().send(task);
		}
	}
	//endregion

	//region Lua function wrappers
	private class InitWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "init";
		}

		@Override
		public int invoke(LuaState L) {
			return init(L);
		}
	}

	private class IsSandboxModeWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "isSandboxMode";
		}

		@Override
		public int invoke(LuaState L) {
			return isSandboxMode(L);
		}
	}

	private class GetUserIdWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "getUserId";
		}

		@Override
		public int invoke(LuaState L) {
			return getUserId(L);
		}
	}

	private class GetUserDataWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "getUserData";
		}

		@Override
		public int invoke(LuaState L) {
			return getUserData(L);
		}
	}

	private class LoadProductsWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "loadProducts";
		}

		@Override
		public int invoke(LuaState L) {
			return loadProducts(L);
		}
	}

	private class PurchaseWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "purchase";
		}

		@Override
		public int invoke(LuaState L) {
			return purchase(L);
		}
	}

	private class RestoreWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "restore";
		}

		@Override
		public int invoke(LuaState L) {
			return restore(L);
		}
	}

	private class FinishTransactionWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "finishTransaction";
		}

		@Override
		public int invoke(LuaState L) {
			return finishTransaction(L);
		}
	}
	private class VerifyDRMWrapper implements NamedJavaFunction {
		@Override
		public String getName() {
			return "verify";
		}

		@Override
		public int invoke(LuaState L) {
			return verifyDRM(L);
		}
	}

	//endregion
}
