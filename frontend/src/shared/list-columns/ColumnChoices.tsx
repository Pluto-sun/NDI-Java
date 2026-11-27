import { useState } from 'react';
import type { Field } from './api';
import './listColumns.css';

export function moveColumn(ids: string[], from: string, to: string) {
  const index = ids.indexOf(from);
  const target = ids.indexOf(to);
  if (index < 0 || target < 0 || index === target) return ids;
  const result = [...ids];
  result.splice(index, 1);
  result.splice(target, 0, from);
  return result;
}

export function ColumnChoices({
  fields,
  move,
  selected,
  toggle,
}: {
  fields: Field[];
  move?: (from: string, to: string) => void;
  selected: (id: string) => boolean;
  toggle?: (id: string) => void;
}) {
  const [dragging, setDragging] = useState('');
  return (
    <ol className="column-choices">
      {fields.map(({ id, label }, index) => (
        <li
          key={id}
          className={dragging === id ? 'column-choice-dragging' : undefined}
          onDragOver={(event) => {
            if (move && dragging) event.preventDefault();
          }}
          onDrop={(event) => {
            event.preventDefault();
            if (dragging) move?.(dragging, id);
            setDragging('');
          }}
        >
          {move && (
            <span
              className="column-drag"
              aria-hidden="true"
              draggable
              title="拖动排序"
              onDragStart={(event) => {
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', id);
                setDragging(id);
              }}
              onDragEnd={() => setDragging('')}
            >
              ⠿
            </span>
          )}
          <label>
            <input
              type="checkbox"
              checked={selected(id)}
              disabled={!toggle}
              onChange={() => toggle?.(id)}
            />
            <span>{label}</span>
          </label>
          {move ? (
            <div className="column-choice-actions">
              <button
                type="button"
                className="text-button"
                aria-label={label + '上移'}
                onClick={() => move(id, fields[index - 1].id)}
                disabled={index === 0}
              >
                上移
              </button>
              <button
                type="button"
                className="text-button"
                aria-label={label + '下移'}
                onClick={() => move(id, fields[index + 1].id)}
                disabled={index === fields.length - 1}
              >
                下移
              </button>
            </div>
          ) : !toggle ? (
            <span className="column-fixed-label">管理员固定</span>
          ) : null}
        </li>
      ))}
    </ol>
  );
}
