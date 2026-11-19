/** 换成十进制文本时保留原始有效数字，不经过浮点数。 */
export function plainAmount(value: string): string {
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(value.trim());
  if (!match) return value;
  const [, sign, integer = '', fraction, fractionOnly = '', exponent = '0'] = match;
  const power = Number(exponent);
  if (!Number.isInteger(power) || Math.abs(power) > 1000) return value;
  const digits = integer + (fraction ?? fractionOnly);
  const point = integer.length + power;
  const whole = (point <= 0 ? '0' : digits.slice(0, point).padEnd(point, '0')).replace(
    /^0+(?=\d)/,
    '',
  );
  const decimal = (point < 0 ? '0'.repeat(-point) + digits : digits.slice(point)).replace(
    /0+$/,
    '',
  );
  return (sign === '-' ? '-' : '') + whole + (decimal ? '.' + decimal : '');
}

/** 金额使用整数分位计算，避免浮点误差。 */
export function parseAmountUnits(value: string): bigint | null {
  value = plainAmount(value);
  if (!/^(?:\d+(?:\.\d{0,4})?|\.\d{1,4})$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  const integer = BigInt(whole || '0');
  if (integer >= 10n ** 16n) return null;
  return integer * 10000n + BigInt(fraction.padEnd(4, '0'));
}

export function formatAmountUnits(units: bigint) {
  return plainAmount(`${units / 10000n}.${(units % 10000n).toString().padStart(4, '0')}`);
}

export const expandScientificAmount = (value: string) =>
  /[eE]/.test(value) ? plainAmount(value) : value;
