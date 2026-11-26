import { formatCockpitMoney } from '../../shared/ui/format';

export function revenueAmount(value: string) {
  return formatCockpitMoney(value);
}

export function revenueCardAmount(value: string) {
  return formatCockpitMoney(value, 3);
}
