import { useSearchParams } from 'react-router-dom';

const categories = ['全部', '网络化', '数字化', '智能化'];

export function RevenueCategoryTabs() {
  const [params, setParams] = useSearchParams();
  const selected = params.get('category') || '全部';
  return (
    <div className="revenue-category-tabs" role="group" aria-label="计收三化分类">
      {categories.map((category) => (
        <button
          key={category}
          type="button"
          className="secondary-button"
          aria-pressed={selected === category}
          onClick={() => {
            const next = new URLSearchParams(params);
            next.delete('page');
            if (category === '全部') next.delete('category');
            else next.set('category', category);
            setParams(next);
          }}
        >
          {category}
        </button>
      ))}
    </div>
  );
}
