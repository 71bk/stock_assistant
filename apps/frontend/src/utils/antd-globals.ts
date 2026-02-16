import { message, Modal, notification } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import type { ModalStaticFunctions } from 'antd/es/modal/confirm';
import type { NotificationInstance } from 'antd/es/notification/interface';

import type { HookAPI } from 'antd/es/modal/useModal';

/**
 * Global Ant Design instances that can be used outside of React components.
 * These are initialized in the App component using App.useApp().
 */
class AntdGlobals {
  public message: MessageInstance = message;
  public modal: HookAPI | Omit<ModalStaticFunctions, 'warn'> = Modal;
  public notification: NotificationInstance = notification;

  public setInstances(
    messageInstance: MessageInstance,
    modalInstance: HookAPI,
    notificationInstance: NotificationInstance
  ) {
    // Directly update the properties to reflect the new instances
    // This allows usages like `antd.message.success()` to work correctly
    // However, destructuring like `const { message } = antd` will still hold the old references if done before initialization.
    // The current export pattern `export const { message: msg ... }` in the original file actually destructures *immediately* at module load time.
    // So `msg` will ALWAYS be the static `message` instance unless we change how we export/use them.
    
    // To fix this properly, we need to export the *container* or use getters, OR mutable exports (let).
    // The previous implementation used a class instance `antd`, but exported properties from it.
    // Let's fix the class to update its internal state, but consumers must use `antd.message` or we need to change how `msg` is exported.
    
    // BUT, the existing code uses `msg.success(...)`. `msg` is imported from this file.
    // `export const { message: msg } = antd;` creates a CONSTANT binding to `antd.message` at that moment in time.
    // Changing `antd.message` later DOES NOT update `msg`.
    
    // We must change the export to be mutable or proxy-based.
    // Given the previous error was about static context, we simply need to ensure we use the context-aware instances.
    
    // Refactoring to use mutable exports:
    this.message = messageInstance;
    this.modal = modalInstance;
    this.notification = notificationInstance;
  }
}

// Singleton instance
export const antd = new AntdGlobals();

// Mutable exports pattern to allow updates
export let msg = antd.message;
export let mdl = antd.modal;
export let ntf = antd.notification;

// We need to override the setInstances to also update these exports
const originalSetInstances = antd.setInstances.bind(antd);
antd.setInstances = (m, mo, n) => {
    originalSetInstances(m, mo, n);
    msg = antd.message;
    mdl = antd.modal;
    ntf = antd.notification;
};
