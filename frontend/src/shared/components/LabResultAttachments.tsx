import { useQuery } from '@tanstack/react-query';
import { fetchLabReportUrl, fetchMachineOutputUrl } from '../api/labResults';
import { useAuth } from '../auth/AuthProvider';

export function LabResultAttachments({ resultId, machineOutputObjectKey, labReportObjectKey }: {
  resultId: string;
  machineOutputObjectKey?: string | null;
  labReportObjectKey?: string | null;
}) {
  const { accessToken } = useAuth();
  const machine = useQuery({
    queryKey: ['lab-results', resultId, 'machine-output-url'],
    queryFn: () => fetchMachineOutputUrl(resultId, accessToken as string),
    enabled: !!accessToken && !!machineOutputObjectKey,
    staleTime: 10 * 60 * 1000,
  });
  const report = useQuery({
    queryKey: ['lab-results', resultId, 'report-url'],
    queryFn: () => fetchLabReportUrl(resultId, accessToken as string),
    enabled: !!accessToken && !!labReportObjectKey,
    staleTime: 10 * 60 * 1000,
  });
  if (!machineOutputObjectKey && !labReportObjectKey) return null;
  return <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-3)', margin: 'var(--space-3) 0' }}>
    {machineOutputObjectKey && (machine.data?.url
      ? <a href={machine.data.url} target="_blank" rel="noopener noreferrer">View machine output PDF</a>
      : <span>{machine.isError ? 'Machine output unavailable' : 'Loading machine output…'}</span>)}
    {labReportObjectKey && (report.data?.url
      ? <a href={report.data.url} target="_blank" rel="noopener noreferrer">View laboratory report PDF</a>
      : <span>{report.isError ? 'Laboratory report unavailable' : 'Loading laboratory report…'}</span>)}
  </div>;
}
