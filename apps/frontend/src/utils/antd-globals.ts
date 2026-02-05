import { message, Modal, notification } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import type { ModalStaticFunctions } from 'antd/es/modal/confirm';
import type { NotificationInstance } from 'antd/es/notification/interface';

/**
 * Global Ant Design instances that can be used outside of React components.
 * These are initialized in the App component using App.useApp().
 */
class AntdGlobals {
  public message: MessageInstance = message;
  public modal: Omit<ModalStaticFunctions, 'warn'> = Modal;
  public notification: NotificationInstance = notification;

  public setInstances(
    messageInstance: MessageInstance,
    modalInstance: Omit<ModalStaticFunctions, 'warn'>,
    notificationInstance: NotificationInstance
  ) {
    this.message = messageInstance;
    this.modal = modalInstance;
    this.notification = notificationInstance;
  }
}

export const antd = new AntdGlobals();
export const { message: msg, modal: mdl, notification: ntf } = antd;
