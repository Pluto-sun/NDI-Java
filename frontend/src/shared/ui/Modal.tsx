import { useLayoutEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

/** 原生对话框负责焦点约束与背景隔离；关闭后焦点回到触发按钮。 */
export function Modal({
  title,
  children,
  renderHeading,
  onClose,
  busy = false,
}: {
  title: string;
  children: ReactNode;
  renderHeading?: (busy: boolean) => ReactNode;
  onClose: () => void;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useLayoutEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const dialog = ref.current;
    dialog?.showModal();
    dialog?.querySelector<HTMLElement>('h2[tabindex="-1"]')?.focus();
    return () => {
      dialog?.close();
      previous?.focus();
    };
  }, []);
  const pending = () => busy || !!ref.current?.querySelector('[aria-busy="true"]');
  return createPortal(
    <dialog
      ref={ref}
      className="app-modal"
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        if (!pending()) onClose();
      }}
    >
      <header className="modal-heading">
        <div className="modal-heading-title">
          {renderHeading ? renderHeading(busy) : <strong>{title}</strong>}
        </div>
        <button
          type="button"
          className="text-button"
          aria-label={`关闭${title}`}
          disabled={busy}
          onClick={() => {
            if (!pending()) onClose();
          }}
        >
          关闭 ×
        </button>
      </header>
      <div className="modal-content">{children}</div>
    </dialog>,
    document.body,
  );
}
