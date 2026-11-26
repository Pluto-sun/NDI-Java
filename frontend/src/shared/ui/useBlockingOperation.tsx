import { useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import './blockingOperation.css';

function BlockingOverlay({ message }: { message: string }) {
  const ref = useRef<HTMLDialogElement>(null);

  useLayoutEffect(() => {
    const dialog = ref.current!;
    dialog.showModal();
    return () => dialog.close();
  }, []);

  return createPortal(
    <dialog
      ref={ref}
      className="blocking-operation"
      aria-label="正在处理"
      onCancel={(event) => event.preventDefault()}
      onKeyDownCapture={(event) => {
        if (['Tab', 'Enter', 'Escape', ' '].includes(event.key)) {
          event.preventDefault();
          event.stopPropagation();
        }
      }}
    >
      <div className="blocking-operation-logo" aria-hidden="true">
        <img src="/blocking-overlay-logo.png" alt="" />
      </div>
      <p role="status" aria-live="polite">
        {message}
      </p>
    </dialog>,
    document.body,
  );
}

/** 用独立的原生模态层盖住当前页面或对话框，并阻止重复提交。 */
export function useBlockingOperation() {
  const controller = useRef<AbortController | null>(null);
  const [message, setMessage] = useState('');

  useLayoutEffect(() => () => controller.current?.abort(), []);

  async function run(label: string, work: (signal: AbortSignal) => Promise<void>) {
    if (controller.current) return;
    const operation = new AbortController();
    controller.current = operation;
    setMessage(label);
    const timeout = window.setTimeout(
      () =>
        operation.abort(
          new DOMException('请求超时，请刷新任务或列表核对处理结果。', 'TimeoutError'),
        ),
      10 * 60_000,
    );
    try {
      await work(operation.signal);
    } finally {
      window.clearTimeout(timeout);
      controller.current = null;
      if (operation.signal.reason?.name !== 'AbortError') setMessage('');
    }
  }

  return { run, overlay: message && <BlockingOverlay message={message} /> };
}
