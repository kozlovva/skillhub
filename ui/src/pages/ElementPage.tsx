import { useParams } from 'react-router-dom';

export default function ElementPage() {
  const { slug } = useParams();
  return <div>Element {slug}</div>;
}
